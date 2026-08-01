def compute(x: Int32, y: Int32): Int32 = {
    let a = x + y * 2;
    let b = a |> double;
    if (b > 100) {
        b / 2
    } else {
        b + 10
    }
}
