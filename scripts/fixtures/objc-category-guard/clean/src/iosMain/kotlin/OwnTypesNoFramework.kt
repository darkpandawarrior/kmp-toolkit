// Fixture for scripts/objc-category-guard.py --self-test. NEVER COMPILED — see TripCases.kt.
// The package anchor: this file imports nothing from platform.CoreSpotlight, so our own `.title`
// and `.keywords` must not be flagged. This is what keeps common English member names usable.
package fixtures.clean

private data class Article(val title: String, val keywords: List<String>)

fun describe(article: Article): String = article.title + article.keywords.size

// A named argument is not a receiver access.
fun label(text: String) = render(contentDescription = text)

private fun render(contentDescription: String) = contentDescription
