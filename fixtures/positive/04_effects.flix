eff Console {
    def printStr(msg: String): Unit
}

// Effect operations are invoked as ordinary calls; `do op(...)` was removed.
def logMessage(): Unit \ Console = {
    Console.printStr("Hello World")
}

// `try ... with` was replaced by `run e with handler E { ... }`.
def main(): Unit = {
    run {
        logMessage()
    } with handler Console {
        def printStr(msg) = ()
    }
}

def guarded(): Unit = {
    try riskyOperation() catch {
        case ex: Exception => ()
    }
}

def scoped(): Unit = region rc {
    let arr = Array#{1, 2, 3} @ rc;
    foreach (x <- arr) Console.printStr("value")
}
