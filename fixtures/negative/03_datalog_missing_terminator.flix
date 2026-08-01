// expect-error: 5:0
// A Datalog constraint must end with a dot followed by whitespace.
def f(): Unit = #{
    Path(x, y) :- Edge(x, y)
}