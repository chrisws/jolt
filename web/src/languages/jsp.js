import * as monaco from 'monaco-editor';

export function registerJspLanguage() {
  monaco.languages.register({ id: 'jsp' });

  monaco.languages.setMonarchTokensProvider('jsp', {
    defaultToken: '',

    root: [
      // JSP comment: <%-- ... --%>
      [/<%--/, { token: 'comment.jsp' }, '@jspComment'],

      // JSP directive: <%@ ... %>
      [/<%@/, { token: 'keyword.directive' }, '@jspDirective'],

      // Expression: <%= ... %>
      [/<%=/, { token: 'operator.expression' }, '@expression'],

      // Scriptlet: <% ... %>
      [/<%/, { token: 'operator.scriptlet' }, '@scriptlet'],

      // EL expression: ${ ... }
      [/\$\{/, { token: 'operator.el' }, '@elExpression'],

      // HTML tag delimiters
      [/</, 'tag.html'],
      [/>/, 'tag.html'],

      // HTML attribute name (word followed by =)
      [/[\w-]+(?=\s*=)/, 'attribute.html'],

      // HTML attribute value (quoted)
      [/"[^"]*"/, 'string.htmlAttr'],
      [/'[^']*'/, 'string.htmlAttr'],
    ],

    jspComment: [
      [/--%>/, { token: 'comment.jsp' }, '@pop'],
      [/./, 'comment.jsp'],
    ],

    jspDirective: [
      [/%>/, { token: 'keyword.directive' }, '@pop'],

      // Directive names
      [/\b(page|include|taglib|directive|tag)\b/, 'keyword.directiveName'],

      // Attribute names in directives (word followed by =)
      [/[\w$]+(?=\s*=)/, 'attribute.directive'],

      // String values
      [/"[^"]*"/, 'string.directiveValue'],
      [/'[^']*'/, 'string.directiveValue'],

      // Delimiters for import lists etc.
      [/[,;]/, 'delimiter.directive'],

      // Numbers (rare in directives but possible)
      [/\d+/, 'number.directive'],
    ],

    scriptlet: [
      [/%>/, { token: 'operator.scriptlet' }, '@pop'],

      // Java line comment
      [/\/\/.*$/, 'comment.java'],

      // Java block comment start
      [/\/\*/, 'comment.java', '@javaBlockComment'],

      // Java string literal
      [/"[^"]*"/, 'string.java'],

      // Java char literal
      [/'[^']*'/, 'string.javaChar'],

      // Control flow keywords
      [/\b(if|else|for|while|do|switch|case|break|continue|return|try|catch|finally|throw|throws)\b/, 'keyword.control'],

      // Modifier keywords
      [/\b(public|private|protected|static|final|abstract|synchronized|volatile|transient)\b/, 'keyword.modifier'],

      // Type/declaration keywords
      [/\b(class|interface|enum|extends|implements)\b/, 'keyword.type'],

      // Literals & special identifiers
      [/\b(new|null|true|false|this|super)\b/, 'keyword.literal'],

      // Primitive types
      [/\b(int|long|float|double|char|boolean|byte|short)\b/, 'type.primitive'],

      // JSP implicit objects
      [/\b(request|response|session|application|out)\b/, 'variable.jspImplicit'],

      // Numbers
      [/\d+(\.\d+)?[fFdDlL]?/, 'number.java'],

      // Operators
      [/[+\-*/%=!<>&|^~?:.]+/, 'operator.java'],

      // Brackets
      [/[{}()[\]]/, '@brackets'],
    ],

    javaBlockComment: [
      [/[^*]+/, 'comment.java', '@javaBlockComment'],
      [/\*+[^*/]/, 'comment.java', '@javaBlockComment'],
      [/\*+\//, { token: 'comment.java' }, '@pop'],
    ],

    expression: [
      [/%>/, { token: 'operator.expression' }, '@pop'],

      // Java line comment
      [/\/\/.*$/, 'comment.java'],

      // Java block comment start
      [/\/\*/, 'comment.java', '@javaBlockComment'],

      // Java string literal
      [/"[^"]*"/, 'string.java'],

      // Java char literal
      [/'[^']*'/, 'string.javaChar'],

      // Control flow keywords (ternary etc.)
      [/\b(if|else|for|while|do|switch|case|break|continue|return|try|catch|finally|throw|throws)\b/, 'keyword.control'],

      // Type/declaration keywords
      [/\b(class|interface|enum|extends|implements)\b/, 'keyword.type'],

      // Literals & special identifiers
      [/\b(new|null|true|false|this|super)\b/, 'keyword.literal'],

      // Primitive types
      [/\b(int|long|float|double|char|boolean|byte|short)\b/, 'type.primitive'],

      // JSP implicit objects
      [/\b(request|response|session|application|out)\b/, 'variable.jspImplicit'],

      // Numbers
      [/\d+(\.\d+)?[fFdDlL]?/, 'number.java'],

      // Operators
      [/[+\-*/%=!<>&|^~?:.]+/, 'operator.java'],

      // Brackets
      [/[{}()[\]]/, '@brackets'],
    ],

    elExpression: [
      [/\}/, { token: 'operator.el' }, '@pop'],

      // EL function calls: fn:name()
      [/\w+(?=\()/, 'function.el'],

      // EL keywords
      [/\b(empty|null|true|false)\b/, 'keyword.el'],

      // EL operators
      [/[+\-*/%=!<>&|^~?:.]+/, 'operator.el'],

      // EL literals
      [/\d+(\.\d+)?/, 'number.el'],
      [/"[^"]*"/, 'string.el'],
      [/'[^']*'/, 'string.el'],
    ],
  });

  // Language configuration: brackets, comments, auto-closing
  monaco.languages.setLanguageConfiguration('jsp', {
    comments: {
      blockComment: ['<%--', '--%>'],
    },
    brackets: [
      ['{', '}'],
      ['[', ']'],
      ['(', ')']
    ],
    autoClosingBrackets: [
      { open: '{', close: '}' },
      { open: '[', close: ']' },
      { open: '(', close: ')' }
    ],
    autoClosingComments: {
      blockComment: ['<%-- ', ' --%>']
    }
  });

  return monaco;
}

export default registerJspLanguage;
