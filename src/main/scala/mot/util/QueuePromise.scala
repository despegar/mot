package mot.util

import java.util.concurrent.BlockingQueue
import mot.IncomingResponse

class QueuePromise(queue: BlockingQueue[IncomingResponse]) extends AbstractQueuePromise[IncomingResponse](queue) {
  def decorate(res: IncomingResponse) = res
}
