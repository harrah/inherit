import sbt._

class InheritProject(info: ProjectInfo) extends DefaultProject(info)
{
	override def managedStyle = ManagedStyle.Maven
}