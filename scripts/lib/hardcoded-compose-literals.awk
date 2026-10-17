# Finds string literals that Compose UI code puts on screen. Used by
# scripts/check_hardcoded_compose_literals.sh, which runs it twice over the same files:
#
#   phase=1  prints the parameter names of every @Composable function ("P<TAB>name<TAB>index<TAB>param")
#            and every file that declares a composable ("F<TAB>path").
#   phase=2  reads that output from its first input file, then prints "path:line: rule: "text"" for
#            each literal that reaches the screen.
#
# The scanner reads Kotlin one character class at a time and keeps a stack of open brackets, so it
# knows which call, named argument, assignment or if/when branch each literal belongs to. A literal
# is reported when it contains words and it is:
#
#   - an argument for a text parameter: Text(...), Icon(_, ...), contentDescription, notification text,
#     or any argument whose parameter name reads as text (title, label, message, supportingText, ...),
#     named or positional, of a composable declared in the repository;
#   - in a file that declares composables: a text-named argument of any other call (UI state, actions),
#     a text-named local or default value, or a value chosen by if/when/else that reads as prose.
#
# Literals inside @Preview functions, PreviewParameterProvider classes and top-level preview* or sample*
# declarations are example content and are skipped, as are animation labels, log and exception messages, and annotations.
# The program uses only POSIX awk, so it runs on macOS awk and the mawk of CI's Ubuntu runners.

function reset_file() {
  sp = 0
  set_frame(0, "TOP", "")
  fline[0] = 0
  msp = 0
  mode[0] = "C"
  bcdepth = 0
  prevTok = ""
  prevClosed = ""
  lastLineTok = ""
  atPending = 0
  pendingPreview = 0
  pendingPreviewLevel = -1
  pendingComposable = 0
  pendingComposableLevel = -1
  pendingClass = 0
  pendingClassLevel = -1
}

# Frame kinds: TOP (file), CLS (class body), L (function body or lambda), BR (if/else body),
# WHEN (when body), "(" (call arguments), P (declared parameters), "[" (index), T (string template).
function set_frame(k, kind, name) {
  fk[k] = kind
  fname[k] = name
  fargi[k] = 0
  fnamed[k] = ""
  fclean[k] = 1
  fassign[k] = ""
  fdecl[k] = 0
  farrow[k] = 0
  fcomp[k] = 0
  fpreviewDecl[k] = 0
  fline[k] = FNR
  fcol[k] = length(cl)
  fprev[k] = (k > 0) ? (fprev[k - 1] || fpreviewDecl[k - 1]) : 0
}

function push(kind, name) {
  fclean[sp] = 0
  sp++
  set_frame(sp, kind, name)
}

function pop(   name) {
  name = fname[sp]
  if (sp > 0) sp--
  return name
}

function trailing_name(s, pat) {
  if (match(s, pat)) {
    s = substr(s, RSTART, RLENGTH)
    sub(/[ \t]+$/, "", s)
    return s
  }
  return ""
}

function base_name(s) {
  sub(/^.*\./, "", s)
  return s
}

# Template expressions and escapes are already blanked; format specifiers carry no words either.
function visible_words(s) {
  gsub(/%[0-9]*\$?[-#+ 0,(]*[0-9]*(\.[0-9]+)?[a-zA-Z]/, " ", s)
  return s
}

# Capitalized words or a phrase, as opposed to keys, tags, identifiers and date patterns.
function is_prose(s) {
  if (s ~ /^[ dMyHhmsaEL,.:\/'-]+$/) return 0
  if (s ~ /^[^A-Za-z]*[A-Z][a-z]/) return 1
  if (s ~ /[A-Za-z][^ ]* +[^ ]*[A-Za-z]/) return 1
  return 0
}

function is_text_name(n) {
  if (n ~ /^(animation|transition)/) return 0
  if (n ~ /^(text|label|title|headline|body|placeholder|contentDescription|stateDescription|subtitle|supportingText|actionLabel|hint|description|message|error|caption|tooltip|summary)$/) return 1
  if (n ~ /[a-z](Label|Text|Title|Description|Headline|Placeholder|Hint|Message)$/) return 1
  return 0
}

# Lambdas whose last expression becomes the value, as in title.ifEmpty { "Untitled" }.
function is_value_scope(c) {
  c = base_name(c)
  return c ~ /^(ifEmpty|ifBlank|let|run|takeIf|takeUnless|orEmpty|getOrElse|lazy)$/
}

function is_composable(c) {
  c = base_name(c)
  return (c in composables) || c ~ /^(Text|BasicText|Icon|Image|AsyncImage|SubcomposeAsyncImage|ContextMenuItem|Window|DialogWindow|Menu|Item|CheckboxItem|RadioButtonItem|Tray|showSnackbar)$/
}

function is_notification_text(c) {
  return base_name(c) ~ /^(setContentText|setContentTitle|setSubText|setTicker|bigText|setBigContentTitle|setSummaryText)$/
}

function positional_param(c, idx,   b) {
  b = base_name(c)
  if ((b SUBSEP idx) in params) return params[b SUBSEP idx]
  if (idx == 0 && b ~ /^(Text|BasicText)$/) return "text"
  if (idx == 1 && b ~ /^(Icon|Image|AsyncImage|SubcomposeAsyncImage)$/) return "contentDescription"
  if (idx == 0 && b == "ContextMenuItem") return "label"
  if (idx == 0 && b == "showSnackbar") return "message"
  if (idx == 0 && is_notification_text(c)) return "text"
  return ""
}

function is_non_ui_call(c, name) {
  if (c ~ /^@/ || c ~ /^(Napier|Log)\./) return 1
  c = base_name(c)
  if (c ~ /(Exception|Error|Throwable)$/) return 1
  if (name == "label" && (c ~ /^animate/ || c ~ /Transition$/ || c ~ /^(Crossfade|AnimatedContent)$/)) return 1
  return 0
}

# Composables run inside function bodies and lambdas, never directly in a class body or at the top
# level, so an enum entry that shares a composable's name is a declaration.
function in_declaration(k) {
  for (k = k - 1; k >= 0; k--) {
    if (fk[k] == "(" || fk[k] == "[") continue
    return fk[k] == "CLS" || fk[k] == "TOP"
  }
  return 1
}

function argument_rule(k, c, name, idx,   p) {
  if (name == "contentDescription") return "contentDescription ="
  if (name == "") {
    p = positional_param(c, idx)
    if (p == "") return ""
    if (is_notification_text(c)) return base_name(c) "()"
    name = p
  }
  if (!is_text_name(name) || is_non_ui_call(c, name)) return ""
  if (uiFile || (is_composable(c) && !in_declaration(k))) return base_name(c) "(" name ")"
  return ""
}

function assignment_rule(name, decl, chosen, words) {
  if (decl == 0 && name == "contentDescription") return "contentDescription ="
  if (!uiFile) return ""
  if (decl == 1 && is_text_name(name)) return "val " name
  if (decl == 0 && is_text_name(name)) return name " ="
  if (chosen && is_prose(words)) return "chosen by if/when"
  return ""
}

# Walks out from the literal to the call, parameter list or assignment that decides where it ends up.
function classify(words, prev, prevC,    k, t, chosen) {
  if (fprev[sp] || fpreviewDecl[sp]) return ""
  chosen = (prev == "else" || (prev == ")" && prevC == "if"))
  for (k = sp; k >= 0; k--) {
    t = fk[k]
    if (t == "T") continue
    if (t == "(") {
      if (fname[k] ~ /^(if|when|while|for|catch|val|var)$/) return ""
      if (fname[k] ~ /^(Pair|Triple)$/ && fnamed[k] == "") continue
      return argument_rule(k, fname[k], fnamed[k], fargi[k])
    }
    if (t == "P") {
      if ((fcomp[k] || uiFile) && is_text_name(fnamed[k])) return "default " fnamed[k]
      return ""
    }
    if (t == "[") return ""
    if (fassign[k] != "") return assignment_rule(fassign[k], fdecl[k], chosen, words)
    if (t == "BR") { chosen = 1; continue }
    if (t == "WHEN") { if (!farrow[k]) return ""; chosen = 1; continue }
    if (t == "L" && is_value_scope(fname[k])) continue
    break
  }
  if (uiFile && chosen && is_prose(words)) return "chosen by if/when"
  return ""
}

function start_literal(kind) {
  fclean[sp] = 0
  msp++
  mode[msp] = kind
  lit[msp] = ""
  litLine[msp] = FNR
  litStart[msp] = i
  litPrev[msp] = prevTok
  litPrevClosed[msp] = prevClosed
}

function end_literal(   words, rule) {
  if (phase == 2) {
    words = visible_words(lit[msp])
    if (words ~ /[A-Za-z]/) {
      rule = classify(words, litPrev[msp], litPrevClosed[msp])
      if (rule != "") printf "%s:%d: %s: %s\n", FILENAME, litLine[msp], rule, (litLine[msp] == FNR ? substr(line, litStart[msp], i - litStart[msp]) : "\"\"\"" lit[msp] "\"\"\"")
    }
  }
  msp--
  prevTok = "lit"
  prevClosed = ""
  cl = cl "\"\""
}

# A new line ends the statement at block level unless either line continues the expression.
function on_line_start(line,   t) {
  t = fk[sp]
  if (t == "(" || t == "[" || t == "P" || t == "T") return
  if (lastLineTok ~ /^(=|->|\?:|\.|,|\(|&&|\|\||\+|-|\*|\/|else|:)$/) return
  if (line ~ /^[ \t]*(\.|\?|&&|\|\||->|else[^A-Za-z0-9_])/) return
  if (pendingClassLevel == sp) pendingClass = 0
  fassign[sp] = ""
  fdecl[sp] = 0
  fpreviewDecl[sp] = 0
  fclean[sp] = 1
  if (t == "WHEN") farrow[sp] = 0
}

phase == 2 && FILENAME == ARGV[1] {
  split($0, rec, "\t")
  if (rec[1] == "P") { params[rec[2] SUBSEP rec[3]] = rec[4]; composables[rec[2]] = 1 }
  else if (rec[1] == "F") uiFiles[rec[2]] = 1
  next
}

FNR == 1 {
  reset_file()
  uiFile = (FILENAME in uiFiles)
}

{
  line = $0
  n = length(line)
  i = 1
  cl = ""
  if (mode[msp] == "C") on_line_start(line)
  while (i <= n) {
    m = mode[msp]
    rest = substr(line, i)

    if (m == "BC") {
      if (match(rest, /\/\*|\*\//)) {
        tok = substr(rest, RSTART, 2)
        i += RSTART + 1
        if (tok == "/*") bcdepth++
        else if (--bcdepth == 0) msp--
        continue
      }
      break
    }

    if (m == "S" || m == "R") {
      if (!match(rest, m == "S" ? "[\\\\\"$]" : "\"\"\"|[$]")) {
        lit[msp] = lit[msp] rest " "
        break
      }
      lit[msp] = lit[msp] substr(rest, 1, RSTART - 1)
      i += RSTART - 1
      c = substr(line, i, 1)
      if (c == "\\") {
        e = substr(line, i + 1, 1)
        if (e == "u") { lit[msp] = lit[msp] " "; i += 6 }
        else if (e ~ /[ntrb]/) { lit[msp] = lit[msp] " "; i += 2 }
        else { lit[msp] = lit[msp] e; i += 2 }
        continue
      }
      if (c == "$") {
        if (substr(line, i + 1, 1) == "{") {
          lit[msp] = lit[msp] " "
          i += 2
          push("T", "")
          msp++
          mode[msp] = "C"
          continue
        }
        if (match(substr(line, i + 1), /^[A-Za-z_][A-Za-z0-9_]*/)) {
          lit[msp] = lit[msp] " "
          i += 1 + RLENGTH
          continue
        }
        lit[msp] = lit[msp] "$"
        i++
        continue
      }
      if (m == "R") {
        i += 3
        while (substr(line, i, 1) == "\"") i++
      } else {
        i++
      }
      end_literal()
      continue
    }

    if (match(rest, /^[ \t]+/)) { cl = cl substr(rest, 1, RLENGTH); i += RLENGTH; continue }
    if (match(rest, /^[A-Za-z_][A-Za-z0-9_]*/)) {
      w = substr(rest, 1, RLENGTH)
      if (atPending) {
        atPending = 0
        if (w ~ /Preview/ && fk[sp] != "(" && fk[sp] != "P") { pendingPreview = 1; pendingPreviewLevel = sp }
        if (w == "Composable" && fk[sp] != "(" && fk[sp] != "P") {
          pendingComposable = 1
          pendingComposableLevel = sp
          if (phase == 1 && !(FILENAME in uiFiles)) { uiFiles[FILENAME] = 1; printf "F\t%s\n", FILENAME }
        }
      }
      if (w ~ /^(class|object|interface)$/ && prevTok != ":" && fk[sp] != "(" && fk[sp] != "P") { pendingClass = 1; pendingClassLevel = sp }
      else if (w ~ /^(fun|val|var)$/ && pendingClassLevel == sp) pendingClass = 0
      prevTok = w
      prevClosed = ""
      cl = cl w
      i += RLENGTH
      continue
    }
    if (match(rest, /^[0-9][0-9A-Za-z_.]*/)) {
      fclean[sp] = 0
      prevTok = "num"
      cl = cl substr(rest, 1, RLENGTH)
      i += RLENGTH
      continue
    }

    c = substr(rest, 1, 1)
    c2 = substr(rest, 1, 2)
    if (c2 == "//") break
    if (c2 == "/*") { msp++; mode[msp] = "BC"; bcdepth = 1; i += 2; continue }
    if (c == "\"") {
      if (substr(rest, 1, 3) == "\"\"\"") { start_literal("R"); i += 3 }
      else { start_literal("S"); i++ }
      continue
    }
    if (c == "'" && match(rest, /^'(\\.|[^'\\])*'/)) {
      fclean[sp] = 0
      prevTok = "lit"
      cl = cl "''"
      i += RLENGTH
      continue
    }
    if (c == "@") { atPending = 1; fclean[sp] = 0; cl = cl c; i++; continue }
    if (c == "(") {
      name = trailing_name(cl, "[A-Za-z_][A-Za-z0-9_.]*[ \t]*$")
      before = substr(cl, 1, length(cl) - length(name))
      kind = "("
      if (before ~ /(^|[^A-Za-z0-9_@])(fun|class)[ \t]/ && before !~ /[=({]/) kind = "P"
      if (name == "constructor") kind = "P"
      if (before ~ /@[A-Za-z_][A-Za-z0-9_.]*[ \t]*$/ || substr(before, length(before), 1) == "@") name = "@" name
      push(kind, name)
      if (kind == "P" && before ~ /(^|[^A-Za-z0-9_])fun[ \t]/ && base_name(name) ~ /^(preview|sample)[A-Z]/ && (fk[sp - 1] == "TOP" || fk[sp - 1] == "CLS")) {
        pendingPreview = 1
        pendingPreviewLevel = sp - 1
      }
      if (kind == "P" && pendingComposable && pendingComposableLevel == sp - 1 && before ~ /(^|[^A-Za-z0-9_])fun[ \t]/) {
        fcomp[sp] = 1
        fname[sp] = base_name(name)
      }
      if (substr(name, 1, 1) != "@") pendingComposable = 0
      prevTok = "("
      cl = cl c
      i++
      continue
    }
    if (c == ")") { prevClosed = pop(); prevTok = ")"; cl = cl c; i++; continue }
    if (c == "[") { push("[", ""); prevTok = "["; cl = cl c; i++; continue }
    if (c == "]") { pop(); prevTok = "]"; cl = cl c; i++; continue }
    if (c == "{") {
      kind = "L"
      name = ""
      if (prevTok == ")" && prevClosed == "if") kind = "BR"
      else if (prevTok == "else") kind = "BR"
      else if (prevTok == "->" && fk[sp] == "WHEN") kind = "BR"
      else if (prevTok == ")" && prevClosed == "when") kind = "WHEN"
      else if (prevTok == "when") kind = "WHEN"
      else if (prevTok == ")") name = prevClosed
      else if (prevTok ~ /^[A-Za-z_]/) name = prevTok
      previewClass = 0
      if (pendingClass && pendingClassLevel == sp) { kind = "CLS"; pendingClass = 0; previewClass = (cl ~ /PreviewParameterProvider/) }
      push(kind, name)
      if (previewClass) fprev[sp] = 1
      if (pendingPreview && pendingPreviewLevel == sp - 1) { fprev[sp] = 1; pendingPreview = 0 }
      pendingComposable = 0
      prevTok = "{"
      cl = cl c
      i++
      continue
    }
    if (c == "}") {
      if (fk[sp] == "T") { pop(); msp--; i++; continue }
      prevClosed = pop()
      if (sp < pendingClassLevel) pendingClass = 0
      prevTok = "}"
      cl = cl c
      i++
      continue
    }
    if (c == ",") { fargi[sp]++; fnamed[sp] = ""; fclean[sp] = 1; prevTok = ","; cl = cl c; i++; continue }
    if (c == ";") {
      if (fk[sp] !~ /^[(P[]$/) { fassign[sp] = ""; fdecl[sp] = 0; fclean[sp] = 1; farrow[sp] = 0; fline[sp] = FNR; fcol[sp] = length(cl) }
      prevTok = ";"
      cl = cl c
      i++
      continue
    }
    if (c2 == "->") {
      if (fk[sp] == "WHEN") farrow[sp] = 1
      prevTok = "->"
      cl = cl c2
      i += 2
      continue
    }
    if (c2 == "?:" || c2 == "==" || c2 == "!=" || c2 == "<=" || c2 == ">=" || c2 == "+=" || c2 == "-=" || c2 == "*=" || c2 == "/=" || c2 == "&&" || c2 == "||") {
      fclean[sp] = 0
      prevTok = c2
      prevClosed = ""
      cl = cl c2
      i += 2
      if (substr(line, i, 1) == "=") i++
      continue
    }
    if (c == ":" && fk[sp] == "P" && fclean[sp]) {
      pn = trailing_name(cl, "[A-Za-z_][A-Za-z0-9_]*[ \t]*$")
      if (phase == 1 && fcomp[sp] && pn != "") printf "P\t%s\t%d\t%s\n", fname[sp], fargi[sp], pn
      fnamed[sp] = pn
    }
    if (c == "=") {
      t = fk[sp]
      if (t == "(") {
        if (fclean[sp]) fnamed[sp] = trailing_name(cl, "[A-Za-z_][A-Za-z0-9_]*[ \t]*$")
      } else if (t != "P" && t != "[") {
        # Only the code of this statement: from the block's opening brace when it is on this line.
        stmt = (fline[sp] == FNR) ? substr(cl, fcol[sp] + 2) : cl
        if (match(stmt, /(^|[^A-Za-z0-9_])(val|var)[ \t]+[A-Za-z_][A-Za-z0-9_]*/)) {
          dn = substr(stmt, RSTART, RLENGTH)
          sub(/^.*(val|var)[ \t]+/, "", dn)
          fassign[sp] = dn
          fdecl[sp] = 1
          if (dn ~ /^(preview|sample)/ && (t == "TOP" || t == "CLS")) fpreviewDecl[sp] = 1
        } else if (stmt ~ /(^|[^A-Za-z0-9_])fun[ \t]/) {
          fassign[sp] = "fun"
          fdecl[sp] = 2
          if (pendingPreview && pendingPreviewLevel == sp) { fpreviewDecl[sp] = 1; pendingPreview = 0 }
        } else if (stmt ~ /^[ \t]*[A-Za-z_][A-Za-z0-9_.]*[ \t]*$/) {
          an = stmt
          gsub(/[ \t]/, "", an)
          fassign[sp] = base_name(an)
          fdecl[sp] = 0
        }
      }
      fclean[sp] = 0
      prevTok = "="
      prevClosed = ""
      cl = cl c
      i++
      continue
    }
    fclean[sp] = 0
    prevTok = c
    prevClosed = ""
    cl = cl c
    i++
  }
  # Kotlin's single-quoted strings end on their line; recover from anything the scanner misread.
  while (msp > 0 && mode[msp] == "S") msp--
  if (prevTok != "") lastLineTok = prevTok
}
