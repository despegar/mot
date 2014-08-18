package mot.util

import scala.collection.mutable.ListBuffer

// Ojo que una versión muy parecida de esto ya existe en cloudia. Unificar eventualmente.

object Tabler {

  object Alignment extends Enumeration {
    
    val Left = Value(0) 
    val Right = Value(1)
    
    def toFormat(x: Value) = x match {
      case Left => "-"
      case Right => ""
    }
    
  }
  
  case class Col[A](name: String, width: Int, align: Alignment.Value, precision: Int = 0)(implicit val manifest: Manifest[A]) {
    val clazz = manifest.runtimeClass
  }

  // Stabilizing identifiers (circumvent erasure)
  private val StringClass = classOf[String]
  private val FloatClass = classOf[Float]
  private val DoubleClass = classOf[Double]
  private val IntClass = classOf[Int]
  private val LongClass = classOf[Long]
  private val BooleanClass = classOf[Boolean]

  private def formatLetter(clazz: Class[_]) = clazz match {
    case StringClass | BooleanClass => "s"
    case FloatClass | DoubleClass => "f"
    case IntClass | LongClass => "d"
    case _ => throw new Exception("Unsupported type: " + clazz.getName)
  }

  private def drawTable(columns: Col[_]*)(block: (Seq[Any] => Unit) => Unit) = {
    val headerFormats = columns.map(c => "%" + Alignment.toFormat(c.align) + c.width + "s")
    val valueFormats = columns.map { c => 
      val precision = c.precision match {
        case 0 => ""
        case p => "." + p
      }
      "%" + Alignment.toFormat(c.align) + c.width + precision + formatLetter(c.clazz)
    }
    val lines = ListBuffer[String]()
    def printer(values: Seq[Any]) = {
      assert(values.size == columns.size)
      lines += valueFormats.mkString(" ") format (values: _*)
    }
    val header = headerFormats.mkString(" ") format ((columns.map(_.name).toSeq): _*)
    block(printer)
    (header +: lines).mkString("\n")
  }

  // Despite its appearance, this is not copy-paste: I actually typed it all.
  
  def draw[A](c1: Col[A])(block: (A => Unit) => Unit) = 
    drawTable(c1)(w => block(v => w(Seq(v))))

  def draw[A, B](c1: Col[A], c2: Col[B])(block: ((A, B) => Unit) => Unit) = 
    drawTable(c1, c2)(w => block((v1, v2) => w(Seq(v1, v2))))

  def draw[A, B, C](c1: Col[A], c2: Col[B], c3: Col[C])(block: ((A, B, C) => Unit) => Unit) =
    drawTable(c1, c2, c3)(w => block((v1, v2, v3) => w(Seq(v1, v2, v3))))

  def draw[A, B, C, D](c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D])(block: ((A, B, C, D) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4)(w => block((v1, v2, v3, v4) => w(Seq(v1, v2, v3, v4))))

  def draw[A, B, C, D, E](c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E])
      (block: ((A, B, C, D, E) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5)(w => block((v1, v2, v3, v4, v5) => w(Seq(v1, v2, v3, v4, v5))))

  def draw[A, B, C, D, E, F](c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F])
      (block: ((A, B, C, D, E, F) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6)(w => block((v1, v2, v3, v4, v5, v6) => w(Seq(v1, v2, v3, v4, v5, v6))))

  def draw[A, B, C, D, E, F, G](c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G])
      (block: ((A, B, C, D, E, F, G) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6, c7)(w => block((v1, v2, v3, v4, v5, v6, v7) => w(Seq(v1, v2, v3, v4, v5, v6, v7))))

  def draw[A, B, C, D, E, F, G, H]
      (c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H])
      (block: ((A, B, C, D, E, F, G, H) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6, c7, c8)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8) => w(Seq(v1, v2, v3, v4, v5, v6, v7, v8))))

  def draw[A, B, C, D, E, F, G, H, I]
      (c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], c9: Col[I])
      (block: ((A, B, C, D, E, F, G, H, I) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6, c7, c8, c9)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9) => w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9))))

  def draw[A, B, C, D, E, F, G, H, I, J]
      (c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], c9: Col[I], c10: Col[J])
      (block: ((A, B, C, D, E, F, G, H, I, J) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10) => w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10))))

  def draw[A, B, C, D, E, F, G, H, I, J, K]
      (c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K])
      (block: ((A, B, C, D, E, F, G, H, I, J, K) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11) => w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11))))

  def draw[A, B, C, D, E, F, G, H, I, J, K, L]
      (c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12) => w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12))))
      
  def draw[A, B, C, D, E, F, G, H, I, J, K, L, M]
      (c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L], c13: Col[M])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L, M) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13) => 
        w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13))))
      
  def draw[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]
      (c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L], c13: Col[M], c14: Col[O])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L, M, O) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14) => 
        w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14))))
      
  def draw[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P]
      (c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L], c13: Col[M], c14: Col[O], c15: Col[P])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L, M, O, P) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15) => 
        w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15))))       
        
  def draw[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q]
      (c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L], c13: Col[M], c14: Col[O], c15: Col[P], c16: Col[Q])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L, M, O, P, Q) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16) => 
        w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16))))       
        
  def draw[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R]
      (c1: Col[A], c2: Col[B], c3: Col[C], c4: Col[D], c5: Col[E], c6: Col[F], c7: Col[G], c8: Col[H], 
          c9: Col[I], c10: Col[J], c11: Col[K], c12: Col[L], c13: Col[M], c14: Col[O], c15: Col[P], 
          c16: Col[Q], c17: Col[R])
      (block: ((A, B, C, D, E, F, G, H, I, J, K, L, M, O, P, Q, R) => Unit) => Unit) =
    drawTable(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17)(w => 
      block((v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17) => 
        w(Seq(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17))))       
        
}