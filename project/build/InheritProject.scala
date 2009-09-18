import sbt._

class InheritProject(info: ProjectInfo) extends DefaultProject(info) with AutoCompilerPlugins
{
	val repo = ScalaToolsSnapshots
	override def crossScalaVersions = Set("2.7.2", "2.7.3", "2.7.4", "2.7.5", "2.8.0-SNAPSHOT")
}