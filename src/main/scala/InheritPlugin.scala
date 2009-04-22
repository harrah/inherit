/* inherit -- Inheritance Graph Generator
 * Copyright 2009 Mark Harrah
 */
package inherit

import scala.tools.nsc.{plugins, Global, Phase}
import plugins.{Plugin, PluginComponent}

import scala.collection.jcl.{TreeMap, TreeSet}
import scala.collection.mutable.HashMap
import java.io.{BufferedWriter, File, FileWriter, PrintWriter, Writer}

/** Contains the main code for traversing the AST and producing the inheritance graph. The main method
* is 'graph'.*/
abstract class Inherit extends Plugin
{
	// global is the compiler instance provided to the plugin
	import global._
	/** The file to write the graph to.*/
	protected def output: File
	/** Construct the inheritance graph and write it to 'output'.*/
	def graph()
	{
		val out = new PrintWriter(new BufferedWriter(new FileWriter(output)))
		try
		{
			out.println("digraph Inheritance {")
			graph(out)
			out.println("}")
		}
		finally { out.close() }
	}
	/** Construct the inheritance graph and write it to 'out'.*/
	private def graph(out: PrintWriter)
	{
		// map from a class/module to its immediate parents
		val subToSuper = new HashMap[Symbol, Set[Symbol]]
		// include all classes/modules directly in the source
		for(unit <- currentRun.units; node <- unit.body)
		{
			node match
			{
				case cd: ClassDef => processDirect(cd.symbol)
				case md: ModuleDef => processDirect(md.symbol)
				case _ => ()
			}
		}

		def processDirect(sym: Symbol): Unit  =  if(visible(sym)) getParents(sym)
		def getParents(sym: Symbol) = subToSuper.getOrElseUpdate(sym, parents(sym))
		// alias for an immutable empty set of Symbols
		def empty = Set.empty[Symbol]
		// get the parents of the given symbol if it is a class or module
		def parents(definition: Symbol) =
		{
			val sym =
				if(definition.isModule) definition.moduleClass // the module class has the parent information
				else definition
			
			sym match
			{
				case cs: ClassSymbol => classParents(cs)
				case _ => empty
			}
		}
		// get the parents of the given class/module
		def classParents(cs: ClassSymbol) =
		{
			cs.info match
			{
				case cit: ClassInfoType => parentsFromClassInfo(cit)
				case PolyType(_, result) =>
					result match
					{
						case cit: ClassInfoType => parentsFromClassInfo(cit)
						case _ => empty
					}
				case _ => empty
			}
		}
		// get the parents from the class info
		def parentsFromClassInfo(cit: ClassInfoType) = Set(cit.parents.map(_.typeSymbol).filter(filterClass) : _*)

		// all ancestors of a class or module
		val ancestors = new HashMap[Symbol, Set[Symbol]]
		// get the ancestors for the given symbol (recomputed each call)
		def ancestorsImpl(sym: Symbol): Set[Symbol] =
		{
			val parents = getParents(sym)
			parents.flatMap(parent => getAncestors(parent)) ++ parents
		}
		// get the ancestors for the given symbol (memoized)
		def getAncestors(sym: Symbol) = ancestors.getOrElseUpdate(sym, ancestorsImpl(sym))
		// fill in the ancestors for all classes we are looking at
		subToSuper.keySet.foreach(getAncestors)

		// write out the definitions/attributes for each main class/module
		ancestors.keySet.foreach(sym => if(visible(sym)) out.println(makeLabel(sym)))

		// write out the directed edges to parents
		for( (symbol, parents) <- subToSuper if visible(symbol))
		{
			val indirectAncestors: Set[Symbol] = parents.flatMap(getAncestors)
			val uniqueParents = parents.filter(parent => !indirectAncestors.contains(parent))
			for(parent <- uniqueParents)
				out.println(symbol.id + " -> " + parent.id)
		}
	}
	// true if the given symbol is not anonymous
	private def visible(sym: Symbol) = !sym.isAnonymousFunction && !sym.isAnonymousClass
	// converts the symbol into a readable representation, TODO: decode symbols in names
	private def toString(sym: Symbol) = sym.fullNameString
	// produce a definition string for the graph file
	private def makeLabel(sym: Symbol) =
	{
		val label = (if(sym.isModule) "object " else "class ") + toString(sym)
		sym.id + "[shape=box label=\"" + label + "\"]"
	}
	private def filterClass(sym: Symbol) = filterClassName(toString(sym))
	private def filterClassName(name: String) = true//name != "scala.Product" && name != "scala.ScalaObject"
}

/****** Standard compiler plugin configuration. **********/

object InheritPlugin
{
	val PluginName = "inherit"
}
class InheritPlugin(val global: Global) extends Inherit
{
	import global._
	import InheritPlugin._
	
	val name = PluginName
	val description = "A plugin to produce an inheritance graph of the input sources."
	val components = List[PluginComponent](Component)
	
	/** The output file, currently hard-coded. */
	val output = (new File(settings.outdir.value, "../inherit/inherit.dot")).getAbsoluteFile
	output.getParentFile.mkdirs()

	/* For source compatibility between 2.7.x and 2.8.x */
	private object runsBefore { def :: (s: String) = s }
	private abstract class CompatiblePluginComponent(afterPhase: String) extends PluginComponent
	{
		val runsAfter = afterPhase :: runsBefore
	}
	private object Component extends CompatiblePluginComponent("typer")
	{
		val global = InheritPlugin.this.global
		val phaseName = InheritPlugin.this.name
		def newPhase(prev: Phase) = new InheritPhase(prev)
	}

	private class InheritPhase(prev: Phase) extends Phase(prev)
	{
		def name = InheritPlugin.this.name
		def run = graph()
	}
}
