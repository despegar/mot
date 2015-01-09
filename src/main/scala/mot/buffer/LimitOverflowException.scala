package mot.buffer

class LimitOverflowException(val limit: Int) 
  extends Exception(s"tried to read beyond the limit of the input buffer ($limit)")