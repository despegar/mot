package mot

trait MotParty {
  def name: String
  def context: Context
  def readerBufferSize: Int
  def writerBufferSize: Int
  def maxAcceptedLength: Int
}