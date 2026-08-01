// expect-error: 4:19
// `do op(...)` was removed from Flix. Guards against reintroducing the phantom
// keyword: `do` is an ordinary name, so this is two juxtaposed expressions.
def f(): Unit = do Foo.bar()