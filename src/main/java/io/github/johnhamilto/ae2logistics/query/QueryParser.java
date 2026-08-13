package io.github.johnhamilto.ae2logistics.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

/**
 * Recursive-descent parser for the query language:
 *
 * <pre>
 * mod:ae2  tag:c:ingots  name:iron  name:"iron ingot"  count >= 10k  craftable
 * stored  damage > 50  signal(factory:cleanup) > 0  @savedquery
 * combined with AND / OR / NOT / parentheses; adjacency is AND.
 * </pre>
 *
 * Numbers accept k/m/b suffixes. Everything is case-insensitive except tag and
 * channel ids.
 */
public final class QueryParser {

    public sealed interface Node permits And, Or, Not, Mod, Tag, Name, Count, Craftable, Stored,
            Damage, Signal, Ref {
    }

    public record And(Node left, Node right) implements Node {
    }

    public record Or(Node left, Node right) implements Node {
    }

    public record Not(Node inner) implements Node {
    }

    public record Mod(String namespace) implements Node {
    }

    public record Tag(Identifier id, TagKey<Item> itemTag, TagKey<Fluid> fluidTag) implements Node {
    }

    public record Name(String substring) implements Node {
    }

    /** op: 0 &lt; 1 &lt;= 2 = 3 &gt;= 4 &gt; */
    public record Count(int op, long value) implements Node {
    }

    public record Craftable() implements Node {
    }

    public record Stored() implements Node {
    }

    public record Damage(int op, long value) implements Node {
    }

    public record Signal(Identifier channel, int op, long value) implements Node {
    }

    public record Ref(String name) implements Node {
    }

    public record Result(@Nullable Node root, @Nullable String error) {
        public boolean ok() {
            return root != null;
        }
    }

    private record Token(int kind, String text) {
        // kinds
        static final int WORD = 0;
        static final int STRING = 1;
        static final int LPAREN = 2;
        static final int RPAREN = 3;
        static final int OP = 4;
        static final int END = 5;
    }

    private final List<Token> tokens;
    private int position;

    private QueryParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Result parse(String source) {
        List<Token> tokens;
        try {
            tokens = lex(source);
        } catch (IllegalArgumentException e) {
            return new Result(null, e.getMessage());
        }
        var parser = new QueryParser(tokens);
        try {
            var root = parser.parseOr();
            if (parser.peek().kind() != Token.END) {
                return new Result(null, "unexpected '" + parser.peek().text() + "'");
            }
            return new Result(root, null);
        } catch (IllegalArgumentException e) {
            return new Result(null, e.getMessage());
        }
    }

    private static List<Token> lex(String source) {
        var tokens = new ArrayList<Token>();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(') {
                tokens.add(new Token(Token.LPAREN, "("));
                i++;
            } else if (c == ')') {
                tokens.add(new Token(Token.RPAREN, ")"));
                i++;
            } else if (c == '"') {
                int end = source.indexOf('"', i + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("unterminated string");
                }
                tokens.add(new Token(Token.STRING, source.substring(i + 1, end)));
                i = end + 1;
            } else if (c == '<' || c == '>' || c == '=') {
                if (c != '=' && i + 1 < source.length() && source.charAt(i + 1) == '=') {
                    tokens.add(new Token(Token.OP, source.substring(i, i + 2)));
                    i += 2;
                } else {
                    tokens.add(new Token(Token.OP, String.valueOf(c)));
                    i++;
                }
            } else if (isWordChar(c)) {
                int start = i;
                while (i < source.length() && isWordChar(source.charAt(i))) {
                    i++;
                }
                tokens.add(new Token(Token.WORD, source.substring(start, i)));
            } else {
                throw new IllegalArgumentException("unexpected character '" + c + "'");
            }
        }
        tokens.add(new Token(Token.END, ""));
        return tokens;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '.' || c == '/'
                || c == '#' || c == '-' || c == '@';
    }

    private Token peek() {
        return tokens.get(position);
    }

    private Token next() {
        return tokens.get(position++);
    }

    private Node parseOr() {
        var left = parseAnd();
        while (isKeyword(peek(), "or")) {
            next();
            left = new Or(left, parseAnd());
        }
        return left;
    }

    private Node parseAnd() {
        var left = parseUnary();
        while (true) {
            var token = peek();
            if (isKeyword(token, "and")) {
                next();
                left = new And(left, parseUnary());
            } else if (token.kind() == Token.LPAREN || token.kind() == Token.STRING
                    || (token.kind() == Token.WORD && !isKeyword(token, "or"))) {
                // adjacency is AND, search-bar style
                left = new And(left, parseUnary());
            } else {
                return left;
            }
        }
    }

    private Node parseUnary() {
        if (isKeyword(peek(), "not")) {
            next();
            return new Not(parseUnary());
        }
        return parsePrimary();
    }

    private Node parsePrimary() {
        var token = next();
        if (token.kind() == Token.LPAREN) {
            var inner = parseOr();
            expect(Token.RPAREN, ")");
            return inner;
        }
        if (token.kind() == Token.STRING) {
            return new Name(token.text().toLowerCase(Locale.ROOT));
        }
        if (token.kind() != Token.WORD) {
            throw new IllegalArgumentException("expected a term, got '" + token.text() + "'");
        }
        var word = token.text();
        var lower = word.toLowerCase(Locale.ROOT);

        if (word.startsWith("@")) {
            var name = word.substring(1);
            if (name.isEmpty()) {
                throw new IllegalArgumentException("@ needs a query name");
            }
            return new Ref(name.toLowerCase(Locale.ROOT));
        }
        switch (lower) {
            case "craftable" -> {
                return new Craftable();
            }
            case "stored" -> {
                return new Stored();
            }
            case "count" -> {
                var cmp = expectComparison("count");
                return new Count((int) cmp[0], cmp[1]);
            }
            case "damage" -> {
                var cmp = expectComparison("damage");
                return new Damage((int) cmp[0], cmp[1]);
            }
            case "signal" -> {
                expect(Token.LPAREN, "( after signal");
                var channelToken = next();
                if (channelToken.kind() != Token.WORD) {
                    throw new IllegalArgumentException("signal needs a channel id");
                }
                var channel = Identifier.tryParse(channelToken.text());
                if (channel == null) {
                    throw new IllegalArgumentException("bad channel '" + channelToken.text() + "'");
                }
                expect(Token.RPAREN, ") after channel");
                var cmp = expectComparison("signal");
                return new Signal(channel, (int) cmp[0], cmp[1]);
            }
            default -> {
            }
        }

        int colon = word.indexOf(':');
        if (colon > 0) {
            var prefix = word.substring(0, colon).toLowerCase(Locale.ROOT);
            var rest = word.substring(colon + 1);
            switch (prefix) {
                case "mod" -> {
                    if (rest.isEmpty()) {
                        throw new IllegalArgumentException("mod: needs a namespace");
                    }
                    return new Mod(rest.toLowerCase(Locale.ROOT));
                }
                case "tag" -> {
                    var id = Identifier.tryParse(rest);
                    if (id == null) {
                        throw new IllegalArgumentException("bad tag '" + rest + "'");
                    }
                    return new Tag(id, TagKey.create(Registries.ITEM, id),
                            TagKey.create(Registries.FLUID, id));
                }
                case "name" -> {
                    if (rest.isEmpty() && peek().kind() == Token.STRING) {
                        return new Name(next().text().toLowerCase(Locale.ROOT));
                    }
                    if (rest.isEmpty()) {
                        throw new IllegalArgumentException("name: needs text");
                    }
                    return new Name(rest.toLowerCase(Locale.ROOT));
                }
                default -> throw new IllegalArgumentException("unknown prefix '" + prefix + ":'");
            }
        }
        throw new IllegalArgumentException("unknown term '" + word + "'");
    }

    /** Returns {op, value}. */
    private long[] expectComparison(String what) {
        var opToken = next();
        if (opToken.kind() != Token.OP) {
            throw new IllegalArgumentException(what + " needs a comparison (< <= = >= >)");
        }
        int op = switch (opToken.text()) {
            case "<" -> 0;
            case "<=" -> 1;
            case "=" -> 2;
            case ">=" -> 3;
            case ">" -> 4;
            default -> throw new IllegalArgumentException("bad operator '" + opToken.text() + "'");
        };
        var valueToken = next();
        if (valueToken.kind() != Token.WORD) {
            throw new IllegalArgumentException(what + " needs a number");
        }
        return new long[] {op, parseNumber(valueToken.text())};
    }

    private static long parseNumber(String text) {
        var lower = text.toLowerCase(Locale.ROOT);
        long multiplier = 1;
        if (lower.endsWith("k")) {
            multiplier = 1_000;
            lower = lower.substring(0, lower.length() - 1);
        } else if (lower.endsWith("m")) {
            multiplier = 1_000_000;
            lower = lower.substring(0, lower.length() - 1);
        } else if (lower.endsWith("b")) {
            multiplier = 1_000_000_000;
            lower = lower.substring(0, lower.length() - 1);
        }
        try {
            return Long.parseLong(lower) * multiplier;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad number '" + text + "'");
        }
    }

    public static boolean compare(long left, int op, long right) {
        return switch (op) {
            case 0 -> left < right;
            case 1 -> left <= right;
            case 2 -> left == right;
            case 3 -> left >= right;
            default -> left > right;
        };
    }

    private void expect(int kind, String what) {
        var token = next();
        if (token.kind() != kind) {
            throw new IllegalArgumentException("expected " + what + ", got '" + token.text() + "'");
        }
    }

    private static boolean isKeyword(Token token, String keyword) {
        return token.kind() == Token.WORD && token.text().equalsIgnoreCase(keyword);
    }
}
