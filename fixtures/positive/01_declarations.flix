mod Math.Geometry {
    pub enum Shape {
        case Circle(Float64),
        case Rectangle(Float64, Float64)
    }

    struct Point {
        x: Float64,
        y: Float64
    }

    pub def area(s: Shape): Float64 = match s {
        case Shape.Circle(r) => 3.14159 * r * r
        case Shape.Rectangle(w, h) => w * h
    }
}
