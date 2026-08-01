def solveGraph(): #{ Edge(String, String), Path(String, String) } = {
    let facts = {
        Edge("A", "B").
        Edge("B", "C").
    };
    let rules = {
        Path(x, y) :- Edge(x, y).
        Path(x, z) :- Path(x, y), Edge(y, z).
    };
    solve (facts <+> rules)
}
