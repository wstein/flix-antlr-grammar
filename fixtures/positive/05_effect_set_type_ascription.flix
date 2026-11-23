// `{ a: Int32 }` is not a record (records use `=`). Parser2 accepts it as an
// effect-set whose element is a type ascription; resolution may still reject it.
type alias R = { a: Int32 }
type alias T[a] = a : Type
