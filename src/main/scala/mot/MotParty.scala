package mot

trait MotParty {
  def name: String
  def context: Context
  def readBufferSize: Int
  def writeBufferSize: Int
  def maxLength: Int
}