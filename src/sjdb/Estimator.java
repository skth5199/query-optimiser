package sjdb;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class Estimator implements PlanVisitor {

	public int estimate = 0;		//declared to compute the cost estimate which will be used by the optimiser using the getEstimate method

	public Estimator() {
		// empty constructor
	}

	public void visit(Scan op) {
		Relation input = op.getRelation();
		//System.out.println(input);
		Relation output = new Relation(input.getTupleCount());

		Iterator<Attribute> iter = input.getAttributes().iterator();
		while (iter.hasNext()) {
			output.addAttribute(new Attribute(iter.next()));
		}
		//System.out.println(output.getAttributes());
		estimate += output.getTupleCount();
		op.setOutput(output);
	}

	public void visit(Project op) {
		Relation input = op.getInput().getOutput();
		Relation output = new Relation(input.getTupleCount());
		Iterator<Attribute> iter = op.getAttributes().iterator();
		while (iter.hasNext()) {
			Attribute att = new Attribute(iter.next());
			try {
				output.addAttribute(new Attribute(input.getAttribute(att)));
			} catch (Exception e) {
				output.addAttribute(new Attribute(att.getName(), 0));
			}
		}
		estimate += output.getTupleCount();
		op.setOutput(output);
	}

	public void visit(Select op) {
		Relation input = op.getInput().getOutput();
		Relation output;
		Attribute r=null,l=null;
		int rc, lc;     //right and left counts
		int vc;         //Count of unique values of the attribute in the new relation (V)
		try {
			r = input.getAttribute(op.getPredicate().getRightAttribute());
			rc = r.getValueCount();
		}
		catch (Exception ex) {		//if the value count could not be fetched
			rc = 0;
		}

		try {
			l = input.getAttribute(op.getPredicate().getLeftAttribute());
			lc = l.getValueCount();
		}
		catch (Exception ex) {		///if the value count could not be fetched
			lc = 0;
			op.setOutput(new Relation(0));
			return;
		}

		if (op.getPredicate().equalsValue()) {        //attr=val case
			output = new Relation(input.getTupleCount() / lc);
			vc = 1;
		}
		else {                                        //attr-attr case
			output = new Relation(input.getTupleCount() / Math.max(lc, rc));
			vc = Math.min(lc, rc);
		}

		Iterator<Attribute> iter = input.getAttributes().iterator();
		while (iter.hasNext()) {
			Attribute att = iter.next();
			if (att.equals(l)) {
				output.addAttribute(new Attribute(att.getName(), vc));
			}
			else if (att.equals(r)) {
				output.addAttribute(new Attribute(att.getName(), vc));
			}
			else {
				output.addAttribute(new Attribute(att));
			}
		}
		estimate += output.getTupleCount();
		op.setOutput(output);
	}

	public void visit(Product op) {
		Relation l = op.getLeft().getOutput();
		Relation r = op.getRight().getOutput();
		int ltc = l.getTupleCount();
		int rtc = r.getTupleCount();
		int ppc = ltc * rtc;
		Relation output = new Relation(ppc);   //relation with the number of tuples equal to that of the product of the number of tuples in 'l' and 'r' relations
		//The two loops below add attributes from l
		Iterator<Attribute> iter = l.getAttributes().iterator();
		while (iter.hasNext()) {
			//Attribute na = new Attribute(iter.next());
			output.addAttribute(new Attribute(iter.next()));
		}
		Iterator<Attribute> iterr = r.getAttributes().iterator();
		while (iterr.hasNext()) {
			output.addAttribute(new Attribute(iterr.next()));
		}
		//System.out.println(output);
		estimate += output.getTupleCount();
		op.setOutput(output);
	}

	public void visit(Join op) {
		Relation l = op.getLeft().getOutput();
		Relation r = op.getRight().getOutput();
		Predicate p = op.getPredicate();
		//Cost estimate formula obtained from lecture slides
		int lc = l.getAttribute(p.getLeftAttribute()).getValueCount();
		int rc = r.getAttribute(p.getRightAttribute()).getValueCount();
		int tc = l.getTupleCount() * r.getTupleCount() / Math.max(lc, rc);
		int vc = Math.min(lc, rc);     //Count of unique values of the attribute in the new relation (V)
		Relation output = new Relation(tc);
		Iterator<Attribute> iter = l.getAttributes().iterator();
		while (iter.hasNext()) {		//iterating and adding attributes from the left relation
			Attribute att = iter.next();
			if (att.equals(p.getLeftAttribute())) {
				output.addAttribute(new Attribute(att.getName(), vc));
			}
			else {
				output.addAttribute(new Attribute(att));
			}
		}
		Iterator<Attribute> iterr = r.getAttributes().iterator();
		while (iterr.hasNext()) {		//iterating and adding attributes from the right relation
			Attribute att = iterr.next();
			if (att.equals(p.getRightAttribute())) {
				output.addAttribute(new Attribute(att.getName(), vc));
			}
			else {
				output.addAttribute(new Attribute(att));
			}
		}
		estimate += output.getTupleCount();
		op.setOutput(output);
	}

	public int getEstimate(Operator plan) {		//made to calculate the cost estimate that will be used in the optimizer
		this.estimate = 0;
		plan.accept(this);
		return this.estimate;
	}
}