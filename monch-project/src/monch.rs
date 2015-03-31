#![feature(old_io)]
#![feature(std_misc)]
#![feature(collections)]
#![feature(test)]
#![feature(io)]
#![feature(libc)]

// logging needs this
#![feature(rustc_private)]

#[macro_use] extern crate log;
extern crate env_logger;
extern crate getopts;
extern crate time;
extern crate test;

use std::old_io::TcpStream;
use std::old_io::BufferedStream;
use std::old_io::BufferedReader;
use std::old_io::BufferedWriter;
use std::old_io::Writer;
use std::old_io::Reader;
use std::old_io::IoResult;
use std::str::from_utf8;
use std::time::duration::Duration;
use getopts::Options;

mod semaphore;
use semaphore::Semaphore;

use std::sync::Arc;
use std::thread;
use std::error::Error;
use std::sync::Mutex;
use std::collections::HashMap;
use test::stats::Stats;
use time::precise_time_ns;

use std::error::FromError;
use std::old_io::IoError;

use std::io;
use std::io::Write;

const MESSAGE_TYPE_HELLO: i8 = 0;
const MESSAGE_TYPE_HEARTBEAT: i8 = 1;
const MESSAGE_TYPE_REQUEST: i8 = 2;
const MESSAGE_TYPE_RESPONSE: i8 = 3;
const MESSAGE_TYPE_BYE: i8 = 6;

static PROGRAM_NAME: &'static str = "monch";

static KEY_VERSION: &'static str = "version";
static KEY_CLIENT_NAME: &'static str = "client-name";
static KEY_MAX_LENGTH: &'static str = "max-length";

static PROTOCOL_VERSION: i8 = 1;
static HEARTBEAT_INTERVAL_SEC: i64 = 5;
static REQUEST_TIMEOUT_SEC: i64 = 5;
static BUFFER_SIZE_KB: usize = 256;

fn send_attributes(sock: &mut Writer, attributes: &Vec<(String, String)>) -> IoResult<()> {
    try!(sock.write_u8(attributes.len() as u8));
    for &(ref name, ref value) in attributes {
        try!(sock.write_u8(name.len() as u8));
        try!(sock.write_str(name));
        try!(sock.write_be_u16(value.len() as u16));
        try!(sock.write_str(&value));
    } 
    Ok(())
}

fn calc_attribute_length(attributes: &Vec<(String, String)>) -> i32 {
	let mut length: i32 = 1;
	for &(ref name, ref value) in attributes {
		length += 1;
		length += name.len() as i32;
		length += 2;
		length += value.len() as i32;
	}
	length
}

fn recv_attributes(sock: &mut Reader) -> IoResult<(Vec<(String, String)>, i32)> {
	let attr_qtty = try!(sock.read_byte());
	let mut bytes_len = 1i32; // length byte
	let mut res: Vec<(String, String)> = Vec::with_capacity(attr_qtty as usize);
	for _ in 0 .. attr_qtty {
		bytes_len += 3; // 1 (name size) + 2 (value size)
		let len_name = try!(sock.read_byte());
		let name_bytes = try!(sock.read_exact(len_name as usize));
		bytes_len += len_name as i32;
		let name = String::from_utf8(name_bytes).ok().unwrap(); // do better
		let len_value = try!(sock.read_be_u16());
		let value_bytes = try!(sock.read_exact(len_value as usize));
		bytes_len += len_value as i32;
		let value = String::from_utf8(value_bytes).ok().unwrap(); // do better
		res.push((name, value));
	}
	Ok((res, bytes_len))
}

fn send_client_hello(sock: &mut Writer, max_length: i32) -> IoResult<()> {
	try!(sock.write_i8(MESSAGE_TYPE_HELLO));
	let attributes = vec![
		(KEY_VERSION.to_string(), PROTOCOL_VERSION.to_string()), 
		(KEY_CLIENT_NAME.to_string(), PROGRAM_NAME.to_string()), 
		(KEY_MAX_LENGTH.to_string(), max_length.to_string())
	];
	let message_length = calc_attribute_length(&attributes);
	try!(sock.write_be_i32(message_length as i32));
	try!(send_attributes(sock, &attributes));
	try!(sock.flush());
	Ok(())
}

fn recv_server_hello(sock: &mut Reader) -> Result<Frame, MyError> {
	let (attributes, attr_len) = try!(recv_attributes(sock));
	let res = HelloData { attributes: attributes, attr_len: attr_len };
	Ok(Frame::Hello(res))
}

fn send_request(sock: &mut Writer, request: &StaticRequestData, request_id: i32) -> IoResult<()> {
	try!(sock.write_i8(MESSAGE_TYPE_REQUEST));
	let fixed_part = 12; // 4 + 4 + 4
	let length = fixed_part + request.attr_len + request.msg.len() as i32;
	try!(sock.write_be_i32(length));
	try!(sock.write_be_i32(request_id));
	try!(sock.write_be_i32(0)); // flow id
	try!(sock.write_be_i32(request.timeout.num_milliseconds() as i32));
	try!(send_attributes(sock, &request.attributes));
	try!(sock.write_str(&request.msg));
	try!(sock.flush());	
	Ok(())
}

fn send_bye(sock: &mut Writer) -> IoResult<()> {
	try!(sock.write_i8(MESSAGE_TYPE_BYE));
	let length = 0;
	try!(sock.write_be_i32(length));
	try!(sock.flush());
	Ok(())
}

fn send_heartbeat(sock: &mut Writer) -> IoResult<()> {
	try!(sock.write_i8(MESSAGE_TYPE_HEARTBEAT));
	try!(sock.write_be_i32(0));
	try!(sock.flush());
	Ok(())
}

#[derive(Debug)]
enum MyError {
	Io(IoError),
	Other(String)
}

impl FromError<IoError> for MyError {
    fn from_error(err: IoError) -> MyError {
        MyError::Io(err)
    }
}

impl FromError<String> for MyError {
    fn from_error(err: String) -> MyError {
        MyError::Other(err)
    }
}

// only received message types needed
#[derive(Debug)]
enum Frame {
	HeartBeat,
	Hello(HelloData),
	Response(ResponseData)
}

#[derive(Debug)]
struct HelloData {
	attributes: Vec<(String, String)>,
	attr_len: i32,
}

#[derive(Debug)]
struct StaticRequestData {
	msg: String, 
	timeout: Duration, 
	attributes: Vec<(String, String)>,
	attr_len: i32,
}

#[derive(Debug)]
struct ResponseData {
	reference: i32, 
	attributes: Vec<(String, String)>, 
	body: Vec<u8>
}

fn recv_frame(sock: &mut Reader) -> Result<Frame, MyError> {
	let message_type = try!(sock.read_byte()) as i8;
	let length = try!(sock.read_be_i32());
	match message_type {
		MESSAGE_TYPE_HEARTBEAT => Ok(Frame::HeartBeat),
		MESSAGE_TYPE_RESPONSE => Ok(try!(recv_response(sock, length))),
		MESSAGE_TYPE_HELLO => Ok(try!(recv_server_hello(sock))),
		other => Err(MyError::Other(format!("Invalid message type, received: {}", other))),
	}
}

fn recv_response(sock: &mut Reader, length: i32) -> Result<Frame, MyError> {
	let reference = try!(sock.read_be_u32());
	let (attr, attr_len) = try!(recv_attributes(sock));
	let remaining_length = length - 4 - attr_len;
	let response = try!(sock.read_exact(remaining_length as usize));
	let frame = ResponseData { 
		reference: reference as i32, 
		attributes: attr, 
		body: response 
	};
	Ok(Frame::Response(frame))
}

fn connect(host: &str, port: u16, timeout: Duration) -> IoResult<TcpStream> {
	//log("resolving %s..." % host)
	//let address = socket.gethostbyname(host)
	let sock = try!(TcpStream::connect_timeout((host, port), timeout));
	Ok(sock)
}

fn parse_attributes(str: &Vec<String>) -> Result<Vec<(String, String)>, String> {
	let mut res: Vec<(String, String)> = vec![];
	for attr_string in str {
		let parts = attr_string.split(":").collect::<Vec<&str>>();
		if parts.len() != 2 {
			return Err(format!("invalid attribute specification: {} -- must be of the form 'name: value'", attr_string));
		}
		res.push((parts[0].to_string(), parts[1].to_string()));
	}
	Ok(res)
}

fn sender_thread(
		sock: &mut Writer, 
		semaphore: Arc<Semaphore>, 
		start_time: u64,
		timeout_total: Duration,	
		sent_times: Arc<Mutex<HashMap<i32, u64>>>, 
		send_count: u32,
		max_length: u32,
		finished: Arc<Semaphore>,
		request: StaticRequestData) -> IoResult<()> {
	try!(send_client_hello(sock, max_length as i32));
	let timeout = Duration::seconds(HEARTBEAT_INTERVAL_SEC);
	let mut i = 0;
	while i < send_count && Duration::nanoseconds((precise_time_ns() - start_time) as i64) < timeout_total {
		let success = semaphore.acquire_timeout(timeout);
		if success {
			{
				let mut st = sent_times.lock().unwrap();
				st.insert(i as i32, precise_time_ns());
			}
			try!(send_request(sock, &request, i as i32));
			i += 1;
		} else {
			try!(send_heartbeat(sock));
		}
	}
	// The receiver will signal the finalization
	let mut fin = false;
	while !fin {
		let success = finished.acquire_timeout(timeout);
		if success {
			fin = true;
		} else {
			try!(send_heartbeat(sock));
		}
	}
	Ok(())
}

struct ReceiverResult {
	first_response: Option<Vec<u8>>,
	response_times: HashMap<u32, f32>,
	ok_count: u32,
	same_size_count: u32,
}

fn receiver_thread(
		sock: &mut Reader, 
		semaphore: Arc<Semaphore>, 
		start_time: u64,
		timeout: Duration,
		sent_times: Arc<Mutex<HashMap<i32, u64>>>, 
		send_count: u32,
		finished: Arc<Semaphore>) -> Result<ReceiverResult, MyError> {
	let frame = try!(recv_frame(sock));
	let server_attributes = match frame {
		Frame::Hello(hello) => hello.attributes,
		other => return Err(MyError::Other(format!("Invalid message type. Expecting hello, received: {:?}", other))),
	};
	println!("Received server hello message: {:?}", server_attributes);
	let mut response_times = HashMap::<u32, f32>::with_capacity(send_count as usize);
	let mut first_response: Option<Vec<u8>> = None;
	let mut first_size: Option<i32> = None;
	let mut first = true;
	let mut i = 0;
	let mut ok_count = 0u32;
	let mut same_size_count = 1u32; // first response has always the same size as itself
	while i < send_count && Duration::nanoseconds((precise_time_ns() - start_time) as i64) < timeout {
		let frame = try!(recv_frame(sock));
		match frame {
			Frame::HeartBeat => {}, // pass
			Frame::Response(ResponseData { reference, attributes, body }) => {
				let reception_time = precise_time_ns(); // as soon as possible
				semaphore.release();
				let sent_time = {
					let mut st = sent_times.lock().unwrap();
					st.remove(&reference).unwrap()
				};
				let delay = ((reception_time - sent_time) / 1000) as u32;
				response_times.insert(i, delay as f32);
				for &(ref name, ref value) in attributes.iter() {
					if *name == "status" {
						if *value == "ok" {
							ok_count += 1;
						}
						break;
					}
				}
				if first {
					println!("First response attributes {:?}", attributes);
					first_response = Some(body.clone());
					first_size = Some(body.len() as i32)
				} else {
					if body.len() as i32 == first_size.unwrap() {
						same_size_count += 1;
					}
				}
				if i % 1000 == 0 {
					print!("\rCompleted {} requests.", i);
					io::stdout().flush();
				}
				i += 1;
				first = false;
			},
			other => return Err(MyError::Other(format!("Invalid message type. Expecting heart-beat or response, received: {:?}", other))),
		}
	}
	finished.release();
	println!("\rCompleted all {} requests.", send_count);
	let res = ReceiverResult {
		first_response: first_response,
		response_times: response_times,
		ok_count: ok_count,
		same_size_count: same_size_count,
	};
	Ok(res)
}

fn main_impl(options: &MonchOptions) -> IoResult<()> {
	println!("This is Mot Benchmark (monch).");
	println!("");
	println!("Benchmarking {}:{}...", &options.host, options.port);
	let start = precise_time_ns();
	print!("Establishing TCP connection...");
	io::stdout().flush();
	let mut sock = try!(connect(&options.host, options.port, options.connect_timeout));
	sock.set_timeout(Some(10000));
	println!("done");
	let connection_time = (precise_time_ns() - start) / 1000;
	let mut bye_writer_sock = BufferedStream::new(sock.clone());
	let mut reader_sock = BufferedReader::with_capacity(BUFFER_SIZE_KB * 1024, sock.clone());
	let mut writer_sock = BufferedWriter::with_capacity(BUFFER_SIZE_KB * 1024, sock.clone());
	let semaphore = Arc::new(Semaphore::new(options.concurrency as isize));
	let finished = Arc::new(Semaphore::new(0));
	let mut times = Vec::<u32>::new();
	let sent_times = Arc::new(Mutex::new(HashMap::<i32, u64>::new()));
	times.resize(options.concurrency as usize, 0);	

	let (finished_snd, finished_rcv) = (finished.clone(), finished.clone());
	let (semaphore_snd, semaphore_rcv) = (semaphore.clone(), semaphore.clone());
	let (sent_times_snd, sent_times_rcv) = (sent_times.clone(), sent_times.clone());

	let sender = thread::scoped(move || {
		let attr_len = calc_attribute_length(&options.attributes);
		let request = StaticRequestData {
			msg: options.message.clone(), 
			timeout: Duration::seconds(REQUEST_TIMEOUT_SEC), 
			attributes: options.attributes.clone(), 
			attr_len: attr_len,
		};
		sender_thread(
			&mut writer_sock, semaphore_snd, start, options.timeout, sent_times_snd, options.number, options.max_length, finished_snd, request).unwrap();
	});
	let receiver = thread::scoped(move || {
		receiver_thread(
			&mut reader_sock, semaphore_rcv, start, options.timeout, sent_times_rcv, options.number, finished_rcv).unwrap()
	});
	sender.join();
	let result = receiver.join();
	try!(send_bye(&mut bye_writer_sock));
	let total_time = (precise_time_ns() - start) / (1000 * 1000);
	let received_count = result.response_times.len();
	let times: Vec<f32> = result.response_times.values().map(|x| *x).collect();
	let response_size = result.first_response.map(|r| r.len() as i32);
	println!("");
	println!("Total time taken for tests: {:>7} ms", total_time);
	println!("Connection time:            {:>7} µs", connection_time);
	println!("Response size:              {:>7} bytes", response_size.unwrap_or(-1));
	println!("Received responses:         {:>7} requests", received_count);
	println!("Time outs:                  {:>7} requests", options.number - received_count as u32);
	println!("OK responses:               {:>7} requests", result.ok_count);
	println!("Same size responses:        {:>7} requests", result.same_size_count);
	println!("");
	println!("Percentage of the requests served within a certain time (µs):");
	println!("         {:>8.0} (shortest request)", times.min());
	for p in vec![50.0, 80.0, 95.0, 99.0, 99.9] {
		println!("  {:>5.1}% {:>8.0}", p, times.percentile(p));	
	}
	println!("  {:>5.1}% {:>8.0} (longest request)", 100.0, times.max());
	Ok(())
}

struct MonchOptions {
	host: String,
	port: u16,
	attributes: Vec<(String, String)>,
	message: String,
	timeout: Duration,
	connect_timeout: Duration,
	number: u32,
	concurrency: u16,
	max_length: u32,
}

fn get_options() -> MonchOptions {
    let args: Vec<String> = std::env::args().collect();
    let mut opts = Options::new();
	opts.optmulti("a", "attributes", "set a request attribute ('name: value')", "ATTRIBUTE");
	opts.reqopt("n", "number", "determine how many request to send", "QUANTITY");
	opts.optopt("c", "concurrency", "determine how many request to send at the same time (default 1)", "QUANTITY");
	opts.optopt("t", "timeout", "hoy much time to wait for the completion of the test, in seconds (default: 60)", "TIMEOUT");
	opts.optopt("T", "connect-timeout", "hoy much time to wait for the establishment of the TCP connection, in milliseconds (default: 5000)", "TIMEOUT");
	opts.reqopt("h", "host", "host to connect to", "HOST");
	opts.reqopt("p", "port", "port to connect to", "PORT");
	opts.optopt("m", "message", "message to send in the body (default: empty message)", "MESSAGE");
	opts.optopt("x", "message", "maximum response length allowed, inbytes (default: 10 MB)", "MAX-LENGTH");
	let options = opts.parse(args.tail()).unwrap_or_else( |fail|
		print_usage(&fail.to_string(), &opts)
	);
	let attributes = parse_attributes(&options.opt_strs("a")).unwrap_or_else( |error|
		print_usage(&error.to_string(), &opts)
	);
	let number_str = options.opt_str("n").unwrap(); // mandatory at getopts level
	let number = number_str.parse::<u32>().unwrap_or_else( |error|
		print_usage(&error.description().to_string(), &opts)
	);
	let concurrency = options.opt_str("c").unwrap_or("1".to_string()).parse::<u16>().unwrap_or_else( |error|
		print_usage(&error.description().to_string(), &opts)
	);
	let timeout_sec = options.opt_str("t").unwrap_or("60".to_string()).parse::<i64>().unwrap_or_else( |error|
		print_usage(&error.description().to_string(), &opts)
	);
	let connect_timeout_ms = options.opt_str("T").unwrap_or("5000".to_string()).parse::<i64>().unwrap_or_else( |error|
		print_usage(&error.description().to_string(), &opts)
	);
	let host = options.opt_str("h").unwrap(); // mandatory at getopts level
	let port_str = options.opt_str("p").unwrap(); // mandatory at getopts level
	let port = port_str.parse::<u16>().unwrap_or_else( |error|
		print_usage(&error.description().to_string(), &opts)
	);
	let message = options.opt_str("m").unwrap_or("".to_string());
	let max_length = options.opt_str("x").unwrap_or((10 * 1024 * 1024).to_string()).parse::<u32>().unwrap_or_else( |error|
		print_usage(&error.description().to_string(), &opts)
	);
	MonchOptions {
		host: host,
		port: port, 
		attributes: attributes,
		message: message,
		timeout: Duration::seconds(timeout_sec),
		connect_timeout: Duration::milliseconds(connect_timeout_ms),
		number: number,
		concurrency: concurrency,
		max_length: max_length,
	}
}

fn exit(exit_code: i32) -> ! {
	extern crate libc;
    unsafe {
    	libc::exit(exit_code);
    }
}

fn print_usage(error: &str, opts: &Options) -> ! {
	println!("{}", error);
    print!("{}", opts.usage(&format!("Usage {} [options]", PROGRAM_NAME)));
    exit(2);
}

fn main() {
	env_logger::init().unwrap();
    let opt = get_options();
	let res = main_impl(&opt);
	match res {
		Ok(()) => 
			(),
		Err(err) => {
			println!("{}", err);
			exit(1);
		}
	}
}
