package mot.util

import scala.collection.mutable.ListBuffer
import scala.collection.immutable

object LiveTabler {

  import Tabler._
  import immutable.Seq
    
  val headerInterval = 20
    
  private def drawTable(partWriter: String => Unit, columns: Col[_]*)(block: (Seq[Any] => Unit) => Unit) = {
    val headerFormats = columns.map(c => "%" + Alignment.toFormat(c.align) + c.width + "s")
    val valueFormats = columns.map { c => 
      val precision = c.precision match {
        case 0 => ""
        case p => "." + p
      }
      "%" + Alignment.toFormat(c.align) + c.width + precision + formatLetter(c.clazz)
    }
    val lines = ListBuffer[String]()
    var i = 0
    val headers = headerFormats.mkString(" ") format ((columns.map(_.name).toSeq): _*)
    def printer(values: immutable.Seq[Any]) = {
      assert(values.size == columns.size)
      if (i % headerInterval == 0)
        partWriter(headers)
      partWriter(valueFormats.mkString(" ") format (values: _*))
      i += 1
    }
    block(printer)
  }

  // Despite its appearance, this is not copy-paste: I actually typed it all.
  
  def draw[A](writer: String => Unit, c1: Col[A])(block: (A => Unit) => Unit) = 
    drawTable(writer, c1)(w => block(v => w(Seq(v))))

  def draw[A, B](writer: String => Unit, c1: Col[A], c2: Col[B])(block: ((A, B) => Unit) => Unit) = 
    drawTable(writer, c1, c2)(w => block((v1, v2) => w(Seq(v1, v2))))

  def draw[A, B, C](writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C])(block: ((A, B, C) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3)(w => block((v1, v2, v3) => w(Seq(v1, v2, v3))))

  def draw[A, B, C, D](writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D])(block: ((A, B, C, D) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4)(w => block((v1, v2, v3, v4) => w(Seq(v1, v2, v3, v4))))

  def draw[A, B, C, D, E](writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E])
      (block: ((A, B, C, D, E) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5)(w => block((v1, v2, v3, v4, v5) => w(Seq(v1, v2, v3, v4, v5))))

  def draw[A, B, C, D, E, F](writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F])
      (block: ((A, B, C, D, E, F) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6)(w => block((v1, v2, v3, v4, v5, v6) => w(Seq(v1, v2, v3, v4, v5, v6))))

  def draw[A, B, C, D, E, F, G](writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G])
      (block: ((A, B, C, D, E, F, G) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6, c7)(w => block((v1, v2, v3, v4, v5, v6, v7) => w(Seq(v1, v2, v3, v4, v5, v6, v7))))

  def draw[A, B, C, D, E, F, G, H]
      (writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H])
      (block: ((A, B, C, D, E, F, G, H) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6, c7, c8)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8) => w(Seq(v1, v2, v3, v4, v5, v6, v7, v8))))

  def draw[A, B, C, D, E, F, G, H, I]
      (writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], c9: Col[I])
      (block: ((A, B, C, D, E, F, G, H, I) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6, c7, c8, c9)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9) => w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9))))

  def draw[A, B, C, D, E, F, G, H, I, J]
      (writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], c9: Col[I], c10: Col[J])
      (block: ((A, B, C, D, E, F, G, H, I, J) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10) => w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10))))

  def draw[A, B, C, D, E, F, G, H, I, J, K]
      (writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K])
      (block: ((A, B, C, D, E, F, G, H, I, J, K) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11) => w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11))))

  def draw[A, B, C, D, E, F, G, H, I, J, K, L]
      (writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12) => w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12))))
      
  def draw[A, B, C, D, E, F, G, H, I, J, K, L, M]
      (writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L], c13: Col[M])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L, M) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13) => 
        w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13))))
      
  def draw[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]
      (writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L], c13: Col[M], c14: Col[O])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L, M, O) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14) => 
        w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14))))
      
  def draw[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P]
      (writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L], c13: Col[M], c14: Col[O], c15: Col[P])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L, M, O, P) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15) => 
        w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15))))       
        
  def draw[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q]
      (writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L], c13: Col[M], c14: Col[O], c15: Col[P], c16: Col[Q])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L, M, O, P, Q) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16) => 
        w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16))))       
        
  def draw[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R]
      (writer: String => Unit, c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L], c13: Col[M], c14: Col[O], c15: Col[P], 
          c16: Col[Q], c17: Col[R])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L, M, O, P, Q, R) => Unit) => Unit) =
    drawTable(writer, c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17) => 
        w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17))))       
        
}