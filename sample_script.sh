# only keep nodes in scala.collection and immediate parents that aren't scala.ScalaObject, java.lang.Object, or scala.Product
gvpr 'E[index(tail.label, "scala.collection.") > -1 && index(head.label, "scala.ScalaObject") == -1
   && index(head.label, "java.lang.Object") == -1 && index(head.label, "scala.Product") == -1] {}' inherit.dot |
# discard nodes without edges
gvpr 'N[degree > 0] {} E{}'  |
# make classes blue and objects red and shorten some names
sed -e 's/label="class /color=blue label="/' \
 -e  's/label="object /color=red label="/' \
 -e 's/label="scala.collection./label="c./' \
 -e 's/label="c.immutable./label="c.immut./' \
 -e 's/label="c.mutable./label="c.mut./' \
 -e 's/label="c.generic./label="c.gen./' \
 -e 's/ViewTemplate/VT/' |
# visualize
dot -Granksep=1.7 -Gnodesep=1.2 -Grankdir=BT -Edir=back -Tsvg -o inherit.svg
