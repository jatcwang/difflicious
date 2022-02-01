// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [  
    {
      "title": "Best Practices and Frequently Asked Questions",
      "url": "/difflicious/docs/best-practices-and-faq",
      "content": "Best Practices Managing Differ instances Tests are often the last check for the correctness of your program before it gets deployed, so we need to Here are some tips to help you best manage Differ instances when using difflicious for testing: Only put unmodified derived Differ instances in the implicit scope This avoids the scenario where a modified Differ is pulled in accidentally during derivation, which can results in passing tests that otherwise should fail. If you need a modified Differ instance to be used in a derivation, scope it locally object DifferInstances { implicit val personDiffer: Differ[Person] = Differ.derived[Person] val personByNameSeqDiffer: Differ[List[Person]] = Differ[List[Person]].pairBy(_.name) } // ...Somewhere else val schoolDiffer: Differ[School] = { implicit val personByNameSeqDiffer: Differ[List[Person]] = DifferInstances.personByNameSeqDiffer Differ.derived[School] } Frequently Asked Questions Where is fully automatic derivation for Differs? Fully automatic derivation is intentionally left out for compile time reasons. With automatic derivation, the compiler will derive the instances every time it is needed. This very frequently leads to extremely long compile times which isn’t worth the few lines of code it saves you. How is difflicious different from other projects that provides diffs? MUnit: MUnit’s assertEquals comes out of the box with diff output for case classes. Users do not need to do anything to get the diff output due to its simplicity it isn’t really configurable. It is a good idea to start with MUnit’s assertEquals and only use Difflicious when you need its configurability for more complex assertion failures. DiffX: DiffX is one of the inspirations of this library and Difflicious aims to support all DiffX features/use cases. Feature-wise, difflicious has: Better collection diffing: Difflicious allows you to specify how Seq/Set elements are paired for comparison. Pairing also allows you compare Seqs order-independently. Better configurability: Difflicious takes a more “structured” approach to configurability, where Differ of a complex type can still have all its underlying Differs tweaked or even replaced (using replace). This is handy in some scenarios where you can reuse existing Differs by “swapping” them in and out of a larger Differ. On the other hand, diffx has: Opt-in fully automatic derivation, if you want to convenience and willing to accept longer compile times. Note: Diffx is an actively maintained library, so some comparison may not be up to date and corrections are welcome :) How can I provide a Differ for my newtypes / opaque types? Many Scala users like to use a wrapper type around primitive types for additional type-safety. All ValueDiffer has a contramap method you can use. final case class UserId(value: String) val userIdDiffer: Differ[UserId] = Differ.stringDiffer.contramap(_.value) Note that the type of Differ.stringDiffer is a ValueDiffer (ValueDiffer is a subtype of Differ)"
    } ,    
    {
      "title": "Cheatsheet",
      "url": "/difflicious/docs/cheatsheet",
      "content": "Basic imports import difflicious._ import difflicious.implicits._ Summoning Differ instances val intListDiffer = Differ[List[Int]] Deriving instances for case class and sealed traits (Scala 3 enums) val differ = Differ.derived[Person] For classes with generic fields, you need to also ask for Differ instance of the field type (not just the generic type). case class Box[A]( content: List[A] ) case class Factory[A]( boxes: List[Box[A]] ) implicit def boxDiffer[A](implicit listDiffer: Differ[List[A]]): Differ[Box[A]] = Differ.derived[Box[A]] implicit def factoryDiffer[A](implicit boxesDiffer: Differ[List[Box[A]]]): Differ[Factory[A]] = Differ.derived[Factory[A]] val differ = Differ[Factory[Int]] Configuring Differs val differ = Differ[Map[String, List[Person]]] differ.configure(_.each)(_.pairBy(_.name)) differ.configure(_.each)(_.pairByIndex) differ.ignoreAt(_.each.each.name) // Equivalent to differ.configure(_.each.each.name)(_.ignore) // Replacing a differ at path val anotherPersonListDiffer: Differ[List[Person]] = ??? differ.replace(_.each)(anotherPersonListDiffer)"
    } ,    
    {
      "title": "Configuring Differs",
      "url": "/difflicious/docs/configuring-differs",
      "content": "Configuring Differs In Difflicious, Differs are built to be reconfigurable. This allows you to adapt an existing Differ for each test as needed. Difflicious also supports “deep configuration” where you can tweak how a particular sub-structure of a type is compared. with an intuitive API similar to the ones found in libraries like diffx and Quicklens. Differs are immutable - if you configure it it’ll return a new Differ. Code examples in this page assumes the following import: import difflicious._ import difflicious.implicits._ Basic Configuration Ignore and Unignore You can call .ignore or .unignore on all Differs. This will ignore their diff results and stop it from failing tests. Pair By For Differs of Seq/Set-like data structures, you can call .pairBy or .pairByIndex to change how elements of these data structures are paired up for comparison. Deep configuration using path expressions Difflicious supports configuring a subpart of a Differ with a complex type by using .configure which takes a “path expression” which you can use to express the path to the Differ you want to configure. Differ Type Allowed Paths Explanation Seq .each Traverse down to the Differ used to compare the elements Set .each Traverse down to the Differ used to compare the elements Map .each Traverse down to the Differ used to compare the values of the Map Case Class (any case class field) Traverse down to the Differ for the specified sub type Sealed Trait .subType[SomeSubType] Traverse down to the Differ for the specified sub type Some examples: val differ: Differ[Map[String, List[Person]]] = Differ[Map[String, List[Person]]] // Don't fail if peron's name is different. val differIgnoringPersonName = differ.ignoreAt(_.each.each.name) // .ignoreAt is just a shorthand for configure(...)(_.ignore) so this is equivalent val differIgnoringPersonName2 = differ.configure(_.each.each.name)(_.ignore) // When comparing List[Person], pair the elements by the Person's name val differPairingByPersonName = differ.configure(_.each)(_.pairBy(_.name)) // \"Focusing\" into the Differ for a subtype and ignoring a field val sealedTraitDiffer: Differ[List[MySealedTrait]] = Differ[List[MySealedTrait]] val differWithSubTypesFieldIgnored = sealedTraitDiffer.ignoreAt(_.each.subType[SomeSubType].fieldInSubType) Replace differs You can completely replace the underlying differ at a path using replace. This is useful when you want to reuse an existing Differ you have extensively configured. val mapDiffer: Differ[Map[String, List[Person]]] = Differ[Map[String, List[Person]]] val pairAndCompareByAge = Differ[List[Person]].pairBy(_.age).ignoreAt(_.each.name) val pairByName = Differ[List[Person]].pairBy(_.name) // Use this to compare each person list by age only mapDiffer.replace(_.each)(pairAndCompareByAge) // Use this to compare each person list paired by name mapDiffer.replace(_.each)(pairByName) Unsafe API with configureRaw This is a low-level API that you shouldn’t need in normal usage. All the nice in the previous sections calls this under the hood and it is exposed in case you really need it. configureRaw takes a stringly-typed path to configure the Differ and a raw ConfigureOp. While the API tries to detect errors, there is very little type safety and mistakes can lead to runtime exception. (For example, configureRaw won’t stop you from replacing a Differ with a Differ of the wrong type) def configureRaw(path: ConfigurePath, operation: ConfigureOp): Either[DifferUpdateError, Differ[T]] We need to provide: A path parameter to “travsere” to the Differ you want to cnofigure. Can be the current Differ (ConfigurePath.current), or a Differ embedded inside it. The type of configuration change you want to make e.g. Mark the Differ as ignored Let’s look at some examples: import difflicious.{Differ, ConfigureOp, ConfigurePath} Example: Changing diff of List[Person] to pair elements by name field Let’s say we want to compare the List[Person] independent of element order but instead match by name field… val defaultDiffer: Differ[Map[String, List[Person]]] = Differ[Map[String, List[Person]]] val differPairByName: Differ[Map[String, List[Person]]] = defaultDiffer .configureRaw( ConfigurePath.of(\"each\"), ConfigureOp.PairBy.ByFunc[Person, String](_.name) ).right.get // Try it! differPairByName.diff( Map( \"Germany\" -&gt; List( Person(\"Bob\", 55), Person(\"Alice\", 55), ) ), Map( \"Germany\" -&gt; List( Person(\"Alice\", 56), Person(\"Bob\", 55), ), \"France\" -&gt; List.empty ) ) Map( \"Germany\" -&gt; List( Person( name: \"Bob\", age: 55, ), Person( name: \"Alice\", age: 55 -&gt; 56, ), ), \"France\" -&gt; List( ), ) Example: Ignore a field in a Person when comparing Let’s say we don’t want to take into account the name of the person when comparing… val differPersonAgeIgnored: Differ[Map[String, List[Person]]] = defaultDiffer .configureRaw( ConfigurePath.of(\"each\", \"each\", \"age\"), ConfigureOp.ignore ).right.get // Try it! differPersonAgeIgnored.diff( Map( \"Germany\" -&gt; List( Person(\"Alice\", 55), Person(\"Bob\", 55), ) ), Map( \"Germany\" -&gt; List( Person(\"Alice\", 100), Person(\"Bob\", 100), ), ) ) Map( \"Germany\" -&gt; List( Person( name: \"Alice\", age: [IGNORED], ), Person( name: \"Bob\", age: [IGNORED], ), ), ) When testing (e.g. assertNoDiff) the test would pass because the person’s age is not considered in the comparison."
    } ,    
    {
      "title": "Introduction",
      "url": "/difflicious/docs/introduction",
      "content": "Introduction Difflicious is a library that produces nice readable diffs in your tests. Readable and Actionable diffs Customizability: supporting all kinds of tweaks you’d want to do such as ignoring fields or compare lists independent of element order. Here’s a motivational example! import difflicious._ import difflicious.implicits._ sealed trait HousePet { def name: String } object HousePet { final case class Dog(name: String, age: Int) extends HousePet final case class Cat(name: String, livesLeft: Int) extends HousePet implicit val differ: Differ[HousePet] = Differ.derived } import HousePet.{Cat, Dog} val petsDiffer = Differ[List[HousePet]] .pairBy(_.name) // Match pets in the list by name for comparison .ignoreAt(_.each.subType[Cat].livesLeft) // Don't worry about livesLeft for cats when comparing petsDiffer.diff( obtained = List( Dog(\"Andy\", 12), Cat(\"Dr.Evil\", 8), Dog(\"Lucky\", 5), ), expected = List( Dog(\"Lucky\", 6), Cat(\"Dr.Evil\", 9), Cat(\"Andy\", 12), ) ) And this is the diffs you will see: List( Dog != Cat === Obtained === Dog( name: \"Andy\", age: 12, ) === Expected === Cat( name: \"Andy\", livesLeft: [IGNORED], ), Cat( name: \"Dr.Evil\", livesLeft: [IGNORED], ), Dog( name: \"Lucky\", age: 5 -&gt; 6, ), ) In the example, we can see that: Difflicious spots that Andy is not a Dog but instead a Cat!! The cat Dr.Evil is considered to be the same on both sides, because we decided to not check how many lives the cats have left. A diff is produced showing us that Lucky’s age is wrong."
    } ,    
    {
      "title": "Library Integrations",
      "url": "/difflicious/docs/library-integrations",
      "content": "Library Integrations MUnit Add this to your SBT build \"com.github.jatcwang\" %% \"difflicious-munit\" % \"0.4.0\" % Test and then in your test suites you can call assertNoDiff on any Differ. import munit.FunSuite import difflicious.munit.MUnitDiff._ import difflicious.Differ class MyTest extends FunSuite { test(\"a == b\") { Differ[Int].assertNoDiff(1, 2) } } Scalatest Add this to your SBT build \"com.github.jatcwang\" %% \"difflicious-scalatest\" % \"0.4.0\" % Test Tests should be run with the -oW option to disable Scalatest from coloring test failures all red as it interferes with difflicious color display. testOnly -- -oW Here’s an example of what a test using difflicious looks like: import org.scalatest.funsuite.AnyFunSuite import difflicious.scalatest.ScalatestDiff._ import difflicious.Differ class MyTest extends AnyFunSuite { test(\"a == b\") { Differ[Int].assertNoDiff(1, 2) } } Cats Differ instances for cats data structures like NonEmptyList and Chain can be found in \"com.github.jatcwang\" %% \"difflicious-scalatest\" % \"0.4.0\" % Test import difflicious.Differ import difflicious.cats.implicits._ import cats.data.{NonEmptyMap, NonEmptyList} val differ: Differ[List[NonEmptyMap[String, NonEmptyList[Int]]]] = Differ[List[NonEmptyMap[String, NonEmptyList[Int]]]] // differ: Differ[List[NonEmptyMap[String, NonEmptyList[Int]]]] = difflicious.differ.SeqDiffer@63771188"
    } ,    
    {
      "title": "Quickstart",
      "url": "/difflicious/docs/quickstart",
      "content": "Quickstart Let’s see how you can use Difflicious in your MUnit tests First, add the following dependency in your SBT configuration: \"com.github.jatcwang\" %% \"difflicious-munit\" % \"0.4.0\" % Test If you are running tests using IntelliJ IDEA’s test runner, you will want to turn off the red text coloring it uses for test failure outputs because it interferes with difflicious’ color outputs. In File | Settings | Editor | Color Scheme | Console Colors | Console | Error Output, uncheck the red foreground color. Let’s say you have some case classes.. case class Person( name: String, age: Int, occupation: String ) To perform diffs with Difflicious, you will need to derive some Differs import munit.FunSuite import difflicious.Differ import difflicious.munit.MUnitDiff._ class ExampleTest extends FunSuite { // Derive Differs for case class and sealed traits implicit val personDiffer: Differ[Person] = Differ.derived[Person] test(\"two list of people should be the same\") { Differ[List[Person]].assertNoDiff( List( Person(\"Alice\", 50, \"Doctor\") ), List( Person(\"Alice\", 40, \"Doctor\"), Person(\"Bob\", 30, \"Teacher\") ) ) } } Run the tests, and you should see a nice failure diff: Pretty right? You should explore the next sections of the documentation and learn about the different Differs and how you can configure them!"
    } ,    
    {
      "title": "Types of Differ",
      "url": "/difflicious/docs/types-of-differs",
      "content": "Types of Differs Here we list the kinds of Differs and how you can use them. The examples below assume the following imports: import difflicious._ import difflicious.implicits._ Value Differs For basic types like Int, Double and String we typically can compare them directly e.g. using equals method. If you have a simple type where you don’t need any advanced diffing, then you can use Differ.useEquals to make a Differ instance for it. case class MyInt(i: Int) object MyInt { implicit val differ: Differ[MyInt] = Differ.useEquals[MyInt](valueToString = _.toString) } MyInt.differ.diff(MyInt(1), MyInt(2)) MyInt(1) -&gt; MyInt(2) Differs for Algebraic Data Types (enums, sealed traits and case classes) You can derive Differ for a case class provided that there is a Differ instance for all your fields. Similarly, you can derive a Differ for a sealed trait (Also called Enums in Scala 3) provided that we’re able to derive a Differ for subclass of the sealed trait (or a Differ instance is already in scope for that subclass) Case class final case class Person(name: String, age: Int) object Person { implicit val differ: Differ[Person] = Differ.derived[Person] } Person.differ.diff( Person(\"Alice\", 40), Person(\"Alice\", 35) ) Person( name: \"Alice\", age: 40 -&gt; 35, ) Sealed trait / Scala 3 Enum // Deriving Differ instance for sealed trait sealed trait HousePet final case class Dog(name: String, age: Int) extends HousePet final case class Cat(name: String, livesLeft: Int) extends HousePet object HousePet { implicit val differ: Differ[HousePet] = Differ.derived[HousePet] } HousePet.differ.diff( Dog(\"Lucky\", 1), Cat(\"Lucky\", 1) ) Dog != Cat === Obtained === Dog( name: \"Lucky\", age: 1, ) === Expected === Cat( name: \"Lucky\", livesLeft: 1, ) Seq Differ Differ for sequences allow diffing immutable sequences like Seq, List, and Vector. By default, Seq Differs will match elements by their index in the sequence. In the example below Bob’s age Alice isn’t expected to be in list Charles is expected but missing val alice = Person(\"Alice\", 30) val bob = Person(\"Bob\", 25) val bob50 = Person(\"Bob\", 50) val charles = Person(\"Charles\", 80) Differ.seqDiffer[List, Person].diff( List(alice, bob50), List(alice, bob, charles) ) List( Person( name: \"Alice\", age: 30, ), Person( name: \"Bob\", age: 50 -&gt; 25, ), Person( name: \"Charles\", age: 80, ), ) Pair by field In many test scenarios we actually don’t care about order of elements, as long as the two sequences contains the same elements. One example of this is inserting multiple records into a database and then retrieving them , where you expect the same records to be returned by not necessarily in the original order. In this case, you can configure a Differ to pair by a field instead. val differByName = Differ[List[Person]].pairBy(_.name) differByName.diff( List(bob50, charles, alice), List(alice, bob, charles) ) When we match by a person’s name instead of index, we can now easily spot that Bob has the wrong age. List( Person( name: \"Bob\", age: 50 -&gt; 25, ), Person( name: \"Charles\", age: 80, ), Person( name: \"Alice\", age: 30, ), ) Map differ Map differ pair entries with the same keys and compare the values. Missing key-values will also be reported in the result. It requires a ValueDiffer instance for the map key type (for display purposes) a Differ instance for the map value type Differ[Map[String, Person]].diff( Map( \"a\" -&gt; alice, \"b\" -&gt; bob ), Map( \"b\" -&gt; bob50, \"c\" -&gt; charles ), ) Map( \"a\" -&gt; Person( name: \"Alice\", age: 30, ), \"b\" -&gt; Person( name: \"Bob\", age: 25 -&gt; 50, ), \"c\" -&gt; Person( name: \"Charles\", age: 80, ), ) Set differ Set differ can diff two Sets by pairing the set elements and diffing them. By default, the pairing is based on matching elements that are equal to each other (using equals). However, you most likely want to pair elements using a field on an element instead for better diffs reports (See next section). Pair by field For the best error reporting, you want to configure SetDiffer to pair by a field. val differByName: Differ[Set[Person]] = Differ[Set[Person]].pairBy(_.name) differByName.diff( Set(bob50, charles, alice), Set(alice, bob, charles) ) Set( Person( name: \"Bob\", age: 50 -&gt; 25, ), Person( name: \"Charles\", age: 80, ), Person( name: \"Alice\", age: 30, ), ) Always ignored Differ Sometimes for certain types you can’t really compare them (e.g. Something that’s not a plain data structure). In that case you can use Differ.alwaysIgnore class CantCompare() val alwaysIgnoredDiffer: Differ[CantCompare] = Differ.alwaysIgnore[CantCompare]"
    } ,    
    {
      "title": "Home",
      "url": "/difflicious/",
      "content": "Difflicious helps you find and compare the differences between values. Readable diff results Flexible diffing logic Ignore unimportant fields when comparing Compare Lists of items independent of order Match Map entries by key and show diffs of the values Integration with test frameworks and popular libraries Installation If you’re using the MUnit test framework: // == SBT == \"com.github.jatcwang\" %% \"difflicious-munit\" % \"0.4.0\" // == Mill == ivy\"com.github.jatcwang::difflicious-munit:0.4.0\" If you’re using ScalaTest test framework: // == SBT == \"com.github.jatcwang\" %% \"difflicious-scalatest\" % \"0.4.0\" // == Mill == ivy\"com.github.jatcwang::difflicious-scalatest:0.4.0\""
    } ,        
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
