package mot

/**
 * Trait that represents Mot parties, either Client or Servers.
 */
trait MotParty {
  def name: String
  def context: Context
  def readBufferSize: Int
  def writeBufferSize: Int
  def maxLength: Int
}