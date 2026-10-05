import re, sys, urllib.request

NAMES = ["wallet","heart-pulse","utensils","car","house","briefcase","plane","dumbbell","book-open","music","film","camera","gamepad-2","shopping-bag","coffee","pill","moon","cloud-sun","phone-call","message-circle","map-pin","party-popper","users","laptop"]
URL = "https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/{}.svg"

def attrs(tag):
    return dict(re.findall(r'([\w-]+)="([^"]*)"', tag))

def to_path(tag, a):
    if tag == "path":
        return a["d"]
    if tag == "circle":
        cx, cy, r = float(a["cx"]), float(a["cy"]), float(a["r"])
        return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0"
    if tag == "line":
        return f"M{a['x1']},{a['y1']} L{a['x2']},{a['y2']}"
    if tag in ("polyline", "polygon"):
        pts = a["points"].replace(",", " ").split()
        pairs = [f"{pts[i]},{pts[i + 1]}" for i in range(0, len(pts), 2)]
        return "M" + " L".join(pairs) + (" Z" if tag == "polygon" else "")
    if tag == "rect":
        x, y, w, h = float(a["x"]), float(a["y"]), float(a["width"]), float(a["height"])
        rx = float(a.get("rx", 0))
        if rx == 0:
            return f"M{x},{y} h{w} v{h} h{-w} Z"
        return (f"M{x + rx},{y} h{w - 2 * rx} a{rx},{rx} 0 0,1 {rx},{rx} v{h - 2 * rx} a{rx},{rx} 0 0,1 {-rx},{rx} "
                f"h{-(w - 2 * rx)} a{rx},{rx} 0 0,1 {-rx},{-rx} v{-(h - 2 * rx)} a{rx},{rx} 0 0,1 {rx},{-rx} Z")
    raise ValueError(tag)

out = ["package `in`.voxagent.mobile.ui.kit", "",
       "import androidx.compose.ui.graphics.Color",
       "import androidx.compose.ui.graphics.SolidColor",
       "import androidx.compose.ui.graphics.StrokeCap",
       "import androidx.compose.ui.graphics.StrokeJoin",
       "import androidx.compose.ui.graphics.vector.ImageVector",
       "import androidx.compose.ui.graphics.vector.addPathNodes",
       "import androidx.compose.ui.unit.dp", "",
       "private val ICON_PATHS: List<List<String>> = listOf("]
for name in NAMES:
    svg = urllib.request.urlopen(URL.format(name)).read().decode()
    paths = []
    for tag, rest in re.findall(r"<(path|circle|line|polyline|polygon|rect)\b([^>]*?)/?>", svg):
        paths.append(to_path(tag, attrs(rest)))
    out.append("    listOf(" + ", ".join('"' + p.replace('"', '\\"') + '"' for p in paths) + "),")
out += [")", "",
        "private val cache = arrayOfNulls<ImageVector>(ICON_PATHS.size)", "",
        "fun schemaIcon(token: Int): ImageVector? {",
        "    if (token !in ICON_PATHS.indices) return null",
        "    return cache[token] ?: ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {",
        "        ICON_PATHS[token].forEach { d ->",
        "            addPath(addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)",
        "        }",
        "    }.build().also { cache[token] = it }",
        "}", ""]
open(sys.argv[1], "w").write("\n".join(out))
