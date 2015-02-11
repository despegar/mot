package mot.proxy

import mot.IncomingResponse
import mot.Responder
import java.util.concurrent.BlockingQueue
import mot.util.AbstractQueuePromise

class ContextualPromise(override val queue: BlockingQueue[(IncomingResponse, Responder)], responder: Responder)
    extends AbstractQueuePromise[(IncomingResponse, Responder)](queue) {
  def decorate(result: IncomingResponse) = (result, responder)
}