/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.components;

import de.neemann.digital.TestExecuter;
import de.neemann.digital.core.Model;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.elements.Wire;
import de.neemann.digital.draw.graphics.Vector;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.draw.model.ModelCreator;
import de.neemann.digital.draw.shapes.ShapeFactory;
import de.neemann.digital.undo.Modification;
import junit.framework.TestCase;

public class WireInserterTest extends TestCase {
    private ElementLibrary library;
    private ShapeFactory shapeFactory;

    @Override
    protected void setUp() {
        library = new ElementLibrary();
        shapeFactory = new ShapeFactory(library);
    }

    private VisualElement element(String name, int x, int y) {
        return new VisualElement(name).setShapeFactory(shapeFactory).setPos(new Vector(x, y));
    }

    /**
     * An input connected by a wire to an output. A NOT dropped onto the wire is
     * connected in series, so the output is the inverted input.
     */
    public void testNotInWire() throws Exception {
        Circuit c = new Circuit();
        c.add(element("In", 0, 0));
        c.add(element("Out", 300, 0));
        c.add(new Wire(new Vector(0, 0), new Vector(300, 0)));

        VisualElement not = element("Not", 100, 0);
        c.add(not);
        Modification<Circuit> m = WireInserter.create(c, not);
        assertNotNull(m);
        m.modify(c);

        assertEquals(2, c.getWires().size());

        ModelCreator mc = new ModelCreator(c, library);
        Model model = mc.createModel(false);
        TestExecuter te = new TestExecuter(model).setUp(mc);
        te.check(0, 1);
        te.check(1, 0);
    }

    public void testWireDirectionDoesNotMatter() throws Exception {
        Circuit c = new Circuit();
        c.add(new Wire(new Vector(300, 0), new Vector(0, 0)));
        VisualElement not = element("Not", 100, 0);
        c.add(not);
        WireInserter.create(c, not).modify(c);

        assertEquals(2, c.getWires().size());
        for (Wire w : c.getWires())
            assertTrue(w.p1.x <= 100 && w.p2.x <= 100 || w.p1.x >= 100 && w.p2.x >= 100);
    }

    public void testOnlyInputOnWire() {
        Circuit c = new Circuit();
        c.add(new Wire(new Vector(0, 0), new Vector(300, 0)));
        // the output of an AND with two inputs is not in line with its first input
        VisualElement and = element("And", 100, 0);
        c.add(and);
        assertNull(WireInserter.create(c, and));
    }

    public void testNotBesideWire() {
        Circuit c = new Circuit();
        c.add(new Wire(new Vector(0, 0), new Vector(300, 0)));
        VisualElement not = element("Not", 100, 20);
        c.add(not);
        assertNull(WireInserter.create(c, not));
    }

    public void testPinAtWireEnd() {
        // pins at the end points are connected anyway, nothing to do
        Circuit c = new Circuit();
        c.add(new Wire(new Vector(0, 0), new Vector(100, 0)));
        VisualElement not = element("Not", 100, 0);
        c.add(not);
        assertNull(WireInserter.create(c, not));
    }
}
