package eu.inn.parser

import fastparse.all._

class ExpressionEngine(evaluationEngine: EvaluationEngine) {
  import ExpressionEngine._

  def parse(ifExpression: String): Boolean = {
    var result: Option[Boolean] = None
    aggregationFunctions.foreach { function ⇒
      if (result.isEmpty)
        function.parser.parse(ifExpression) match {
          case Parsed.Success((left, right), _) ⇒
            result = Some(function.op(parse(left), parse(right)))
          case Parsed.Failure(_, _, _) ⇒ // ignore
        }
    }
    result getOrElse parseSimpleExpression(ifExpression)
  }

  def parseSimpleExpression(expr: String): Boolean = {
    var result: Option[Boolean] = None
    simpleFunctions.foreach { function ⇒
      if (result.isEmpty)
        function.parser.parse(expr) match {
          case Parsed.Success((left, right), _) ⇒
            result = Some(function.op(eval(left), eval(right)))
          case Parsed.Failure(_, _, _) ⇒ // ignore
        }
    }
    result match {
      case Some(value) ⇒
        value
      case None ⇒
        eval(expr) match {
          case expressionValue: Boolean ⇒ expressionValue
          case _ ⇒ throw new IllegalArgumentException(s"Value of expression '$expr' is not of Boolean type, please check it and correct")
        }
    }
  }

  def eval(expr: String): Any = {
    not.parse(expr) match {
      case Parsed.Success(parsedExpr, _) ⇒
        !eval(parsedExpr).asInstanceOf[Boolean]
      case Parsed.Failure(_, _, _) ⇒
        if (Ip.isIp(expr))
          expr
        else
          IpRange.parse(expr) match {
            case Some(ipRange) ⇒
              ipRange
            case None ⇒
              evaluationEngine.evaluate(expr)
          }
    }
  }
}

object ExpressionEngine {
  val phrase = CharIn('a' to 'z', 'A' to 'Z', '0' to '9', "!", ".", "'", "\"", "=", "-", " ")
  val wordWithDots = CharIn('a' to 'z', 'A' to 'Z', '0' to '9', "!", ".", "'", "\"", "-")

  val or = P("(" ~ phrase.rep.! ~ ")" ~ " or " ~ AnyChar.rep.!)
  val and = P("(" ~ phrase.rep.! ~ ")" ~ " and " ~ AnyChar.rep.!)
  val eq = P("(".? ~ wordWithDots.rep.! ~ " ".? ~ "=" ~ " ".? ~ wordWithDots.rep.! ~ ")".?)
  val nEq = P("(".? ~ wordWithDots.rep.! ~ " ".? ~ "!=" ~ " ".? ~ wordWithDots.rep.! ~ ")".?)
  val has = P("(".? ~ wordWithDots.rep.! ~ " has " ~ wordWithDots.rep.! ~ ")".?)
  val hasNot = P("(".? ~ wordWithDots.rep.! ~ " has not " ~ wordWithDots.rep.! ~ ")".?)
  val in = P("(".? ~ wordWithDots.rep.! ~ " in " ~ "(".? ~ phrase.rep.! ~ ")".?)
  val notIn = P("(".? ~ wordWithDots.rep.! ~ " not in " ~ "(".? ~ phrase.rep.! ~ ")".?)
  val not = P("(".? ~ "!" ~ wordWithDots.rep.! ~ ")".?)

  val orFunction = BooleanFunction(or, (a, b) ⇒ a.asInstanceOf[Boolean] || b.asInstanceOf[Boolean])
  val andFunction = BooleanFunction(and, (a,b) ⇒ a.asInstanceOf[Boolean] && b.asInstanceOf[Boolean])
  val eqFunction = BooleanFunction(eq, (a, b) ⇒ a == b)
  val nEqFunction = BooleanFunction(nEq, (a, b) ⇒ a != b)
  val hasFunction = BooleanFunction(has, (a, b) ⇒ a.asInstanceOf[Seq[_]].contains(b))
  val hasNotFunction = BooleanFunction(hasNot, (a, b) ⇒ !a.asInstanceOf[Seq[_]].contains(b))
  val inFunction = BooleanFunction(in, (a, b) ⇒ {
    (a, b) match {
      case (ip: String, ipRange: IpRange) ⇒
        val ip = a.asInstanceOf[String]
        val ipRange = b.asInstanceOf[IpRange]
        ipRange.contains(ip)
      case _ ⇒
        b.asInstanceOf[Seq[_]].contains(a)
    }
  })
  val notInFunction = BooleanFunction(notIn, (a, b) ⇒ !inFunction.op(a, b))

  val aggregationFunctions = Seq(andFunction, orFunction)
  val simpleFunctions = Seq(eqFunction, nEqFunction, hasFunction, hasNotFunction, inFunction, notInFunction)

  def apply(evaluationEngine: EvaluationEngine): ExpressionEngine = {
    new ExpressionEngine(evaluationEngine)
  }
}

case class BooleanFunction(parser: Parser[(String, String)], op: (Any, Any) ⇒ Boolean)