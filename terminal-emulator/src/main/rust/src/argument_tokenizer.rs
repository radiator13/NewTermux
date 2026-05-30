/// Shell argument tokenizer — state machine that splits a command string
/// into arguments, handling single quotes, double quotes, and backslash escapes.
/// Ported from ArgumentTokenizer.java (DrJava, Rice University).

#[derive(Clone, Copy, PartialEq)]
enum State {
    NoToken,
    Normal,
    SingleQuote,
    DoubleQuote,
}

/// Tokenize a shell command string into arguments.
/// Returns a Vec of Strings, one per token.
pub fn tokenize(input: &str) -> Vec<String> {
    tokenize_inner(input, false)
}

/// Tokenize with optional stringification (wraps each arg in double quotes
/// and escapes internal quotes/backslashes).
pub fn tokenize_stringify(input: &str) -> Vec<String> {
    tokenize_inner(input, true)
}

fn tokenize_inner(input: &str, stringify: bool) -> Vec<String> {
    let mut args: Vec<String> = Vec::new();
    let mut current = String::new();
    let mut escaped = false;
    let mut state = State::NoToken;

    let chars: Vec<char> = input.chars().collect();
    let len = chars.len();
    let mut i = 0;

    while i < len {
        let c = chars[i];
        if escaped {
            escaped = false;
            current.push(c);
            i += 1;
            continue;
        }

        match state {
            State::SingleQuote => {
                if c == '\'' {
                    state = State::Normal;
                } else {
                    current.push(c);
                }
            }
            State::DoubleQuote => {
                if c == '"' {
                    state = State::Normal;
                } else if c == '\\' {
                    // Look ahead: only escape " or \ inside double quotes
                    i += 1;
                    if i < len {
                        let next = chars[i];
                        if next == '"' || next == '\\' {
                            current.push(next);
                        } else {
                            current.push(c);
                            current.push(next);
                        }
                    } else {
                        current.push(c);
                    }
                } else {
                    current.push(c);
                }
            }
            State::NoToken | State::Normal => {
                match c {
                    '\\' => {
                        escaped = true;
                        state = State::Normal;
                    }
                    '\'' => {
                        state = State::SingleQuote;
                    }
                    '"' => {
                        state = State::DoubleQuote;
                    }
                    _ if c.is_whitespace() => {
                        if state == State::Normal {
                            args.push(current.clone());
                            current.clear();
                            state = State::NoToken;
                        }
                        // NoToken + whitespace = skip
                    }
                    _ => {
                        current.push(c);
                        state = State::Normal;
                    }
                }
            }
        }
        i += 1;
    }

    // Handle trailing state
    if escaped {
        current.push('\\');
        args.push(current);
    } else if state != State::NoToken {
        args.push(current);
    }

    if stringify {
        for arg in &mut args {
            let escaped_arg = escape_quotes_and_backslashes(arg);
            *arg = format!("\"{}\"", escaped_arg);
        }
    }

    args
}

fn escape_quotes_and_backslashes(s: &str) -> String {
    let mut result = String::with_capacity(s.len() + 4);
    for c in s.chars() {
        match c {
            '\\' | '"' => {
                result.push('\\');
                result.push(c);
            }
            '\n' => result.push_str("\\n"),
            '\t' => result.push_str("\\t"),
            '\r' => result.push_str("\\r"),
            '\x08' => result.push_str("\\b"),
            '\x0C' => result.push_str("\\f"),
            _ => result.push(c),
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn simple_args() {
        assert_eq!(tokenize("a b c"), vec!["a", "b", "c"]);
    }

    #[test]
    fn single_quotes() {
        assert_eq!(tokenize("a 'b c' d"), vec!["a", "b c", "d"]);
    }

    #[test]
    fn double_quotes() {
        assert_eq!(tokenize(r#"a "b c" d"#), vec!["a", "b c", "d"]);
    }

    #[test]
    fn backslash_escape() {
        assert_eq!(tokenize(r"a\ b c"), vec!["a b", "c"]);
    }

    #[test]
    fn empty_input() {
        assert_eq!(tokenize(""), Vec::<String>::new());
        assert_eq!(tokenize("   "), Vec::<String>::new());
    }

    #[test]
    fn stringified() {
        let result = tokenize_stringify("a 'b c'");
        assert_eq!(result, vec!["\"a\"", "\"b c\""]);
    }
}
