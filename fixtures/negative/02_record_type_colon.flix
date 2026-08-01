// expect-error: 4:18
// Record types use `=`, never `:`. Using `:` must not be accepted, because that
// is what keeps a record type distinct from a block and from an effect set.
type alias R = { a: Int32 }