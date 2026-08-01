def solveGraph(): #{ Edge(String, String), Path(String, String) } = {
    let facts = #{
        Edge("A", "B").
        Edge("B", "C").
    };
    let rules = #{
        Path(x, y) :- Edge(x, y).
        Path(x, z) :- Path(x, y), Edge(y, z).
    };
    solve (facts <+> rules)
}

def reachable(): #{ Path(String, String) } = {
    let constraints = #{
        Reach(x) :- not Blocked(x), fix Seed(x), if (x > 0).
    };
    query constraints select (x, y) from Path(x, y) where x != y
}
