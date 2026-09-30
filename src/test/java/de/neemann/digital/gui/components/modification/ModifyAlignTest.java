/*
 * Copyright (c) 2026 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.gui.components.modification;

import de.neemann.digital.TestExecuter;
import de.neemann.digital.core.Model;
import de.neemann.digital.draw.elements.Circuit;
import de.neemann.digital.draw.elements.VisualElement;
import de.neemann.digital.draw.elements.Wire;
import de.neemann.digital.draw.graphics.Vector;
import de.neemann.digital.draw.library.ElementLibrary;
import de.neemann.digital.draw.model.ModelCreator;
import de.neemann.digital.draw.shapes.ShapeFactory;
import junit.framework.TestCase;

public class ModifyAlignTest extends TestCase {
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
     * The wire end at the pin of the moved input follows the pin, the other end stays.
     */
    public void testWireFollows() throws Exception {
        Circuit c = new Circuit();
        VisualElement in = element("In", 0, 0);
        c.add(in);
        c.add(element("Out", 300, 40));
        c.add(new Wire(new Vector(0, 0), new Vector(200, 0)));
        c.add(new Wire(new Vector(200, 0), new Vector(200, 40)));
        c.add(new Wire(new Vector(200, 40), new Vector(300, 40)));

        ModifyAlign m = new ModifyAlign();
        m.add(in, new Vector(0, 40));
        m.add(element("Out", 300, 40), new Vector(0, 0)); // not moved, ignored
        m.modify(c);

        assertEquals(new Vector(0, 40), in.getPos());
        boolean found = false;
        for (Wire w : c.getWires())
            if (w.p1.equals(new Vector(0, 40)) || w.p2.equals(new Vector(0, 40)))
                found = true;
        assertTrue(found);

        ModelCreator mc = new ModelCreator(c, library);
        Model model = mc.createModel(false);
        TestExecuter te = new TestExecuter(model).setUp(mc);
        te.check(0, 0);
        te.check(1, 1);
    }

    /**
     * Two equal elements at the same position are both found and moved
     */
    public void testElementsAtSamePosition() throws Exception {
        Circuit c = new Circuit();
        VisualElement a = element("In", 0, 0);
        VisualElement b = element("In", 0, 0);
        c.add(a);
        c.add(b);

        ModifyAlign m = new ModifyAlign();
        m.add(a, new Vector(20, 0));
        m.add(b, new Vector(40, 0));
        assertFalse(m.isEmpty());
        m.modify(c);

        assertEquals(new Vector(20, 0), a.getPos());
        assertEquals(new Vector(40, 0), b.getPos());
    }
}
