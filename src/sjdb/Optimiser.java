package sjdb;

import java.util.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.*;

// @author Srivenkata Srikanth

public class Optimiser implements PlanVisitor {
	private static final Estimator estimator = new Estimator();		//This estimator shall be used for cost estimation
	private Set<Attribute> attributes = new HashSet<>();  		//set of all attributes in the plan
	private Set<Predicate> predicates = new HashSet<>();			//set of all predicates in the plan
	private Set<Scan> scans = new HashSet<>();				//set of all the scans in the plan

	private Catalogue ctg;
	public Optimiser(Catalogue ctg) {	//Similar to an empty constructor
		this.ctg = ctg;				 	//This is done to get relavant estimated costs from estimator
	}

	//The following methods are essential as the optimiser class implements the plan visitor interface
	//These methods are used to add scans predicates and attributes to the sets above respectively
	public void visit(Scan op) {
		scans.add(new Scan((NamedRelation)op.getRelation()));
	}
	public void visit(Project op) {
		attributes.addAll(op.getAttributes());
	}
	public void visit(Product op) {
		//not used
	}
	public void visit(Join op) {
		//not used
		//attributes.addAll(op.getLeft().getAttributes());
		//attributes.addAll(op.getRight().getAttributes());
	}
	public void visit(Select op) {
		predicates.add(op.getPredicate());
		if(op.getPredicate().equalsValue()) {    //if the select is of the form attr=val
			attributes.add(op.getPredicate().getLeftAttribute());
		}
		else{		//for the case of attr=attr
			attributes.add(op.getPredicate().getRightAttribute());
			attributes.add(op.getPredicate().getLeftAttribute());
		}
	}

	public Operator optimise(Operator plan) {
		plan.accept(this);
		//The following steps move the projects and selects down the canonical tree
		List<Operator> planSegs = new ArrayList<>(scans.size());		//Segements of plans
		Scan curs = null;
		Iterator<Scan> iterScans = scans.iterator();
		while(iterScans.hasNext()){		//iterates over scans
			curs = iterScans.next();
			Operator oper = curs;
			List<Attribute> scanAtts = oper.getOutput().getAttributes();		//gets all attributes in scans
			Iterator<Predicate> iterPred = predicates.iterator();
			while(iterPred.hasNext()){
				Predicate currPred = iterPred.next();
				if(oper.getOutput() == null)
					oper.accept(estimator);
				//The if statement checks the form of the predicate
				//if the predicate is of the form attr = val attributes in 'availableAttrs' will be matched to the value and obtained accordingly
				//else the left and the right attributes will be looked for in 'availableAttrs'
				//then the select statements are added with the scan and the current predicate
				if (currPred.equalsValue() && (scanAtts.contains(currPred.getLeftAttribute()))) {
					oper = new Select(oper, currPred);
					iterPred.remove();
				}
				else{
					if (scanAtts.contains(currPred.getLeftAttribute()) && scanAtts.contains(currPred.getRightAttribute())) {
						oper = new Select(oper, currPred);
						iterPred.remove();
					}
				}
			}
			List<Predicate> dupPred = new ArrayList<>();		//predicates array to perform actions on
			dupPred.addAll(predicates);
			Operator pb = null;
			Set<Attribute> resAtts = getResAttrs( plan,dupPred);		//gets the required attributes based on the plan
			if(oper.getOutput() == null)
				oper.accept(estimator);
			List<Attribute> retainedAtts = new ArrayList<>(resAtts);
			retainedAtts.retainAll(oper.getOutput().getAttributes());		//Vital attributes are retained
			//Required projects are created below by taking the intersection of attributes in this iteration of scan and the necessary attributes
			if (retainedAtts.size() > 0) {
				pb = new Project(oper, retainedAtts);
				pb.accept(estimator);
			} else {
				pb = oper;
			}
			planSegs.add(pb);		//The selects and projects are added to the new plan after being brought down the tree
									// in the form of separate blocks which will later be combined
		}
		//In the code below, the joins are created by combining projects and selects where ever possible
		//Following this, the cost is estimated for the combinations of predicates using the estimator
		//and reordering is performed based on the same
		List<Predicate> dupPreds2 = new ArrayList<>();
		dupPreds2.addAll(predicates);
		List<List<Predicate>> permutedPreds = predCombinations(dupPreds2);		// The predicate combinations are found and stored
		Operator planOptimal = null;
		Integer costLow = Integer.MAX_VALUE;
		// Iterate for each predicate combination
		Iterator<List<Predicate>> iterCMB = permutedPreds.iterator();
		while(iterCMB.hasNext()){
			List<Predicate> p = iterCMB.next();
			List<Operator> opFresh = new ArrayList<>();		//creating fresh operators as instructed in the coursework description
			opFresh.addAll(planSegs);
			Operator bestP = null;
			Operator bp = null;
			if (opFresh.size() == 1){		//If there is only one segment, no more computation is necessary and that is returned
				bp = opFresh.get(0);
				if (bp.getOutput() == null)
					bp.accept(estimator);
				bestP = bp;
			}
			else {
				//Predicates are made into joins or selects here
				Iterator<Predicate> iterp = p.iterator();
				while (iterp.hasNext()) {
					Predicate currPred = iterp.next();
					//System.out.println(opFresh);
					//left and right operators are fetched in the two loops below
					Operator left = null;
					Iterator<Operator> iterOPF = opFresh.iterator();
					while(iterOPF.hasNext()){
						Operator curOp = iterOPF.next();
						if (curOp.getOutput().getAttributes().contains(currPred.getLeftAttribute())){
							iterOPF.remove();
							left = curOp;
						}
					}
					//System.out.println("Just got the left op lololololololol");
					//System.out.println(opFresh);
					Operator right = null;
					Iterator<Operator> iterOPF1 = opFresh.iterator();
					while(iterOPF1.hasNext()){
						Operator curOp = iterOPF1.next();
						if (curOp.getOutput().getAttributes().contains(currPred.getRightAttribute())){
							iterOPF1.remove();
							right = curOp;
						}
					}
					//if only one is not null, then a select statement is created
					if ((left == null && right != null) || (right == null && left != null)) {
						if (left != null)
							bp = new Select(left, currPred);
						else
							bp = new Select(right, currPred);
						iterp.remove();
					}
					//if both are not null, a join is created
					if (left != null && right != null) {
						bp = new Join(left, right, currPred);
						iterp.remove();
					}
					//bp.accept(estimator);
					if (bp.getOutput() == null)
						bp.accept(estimator);

					//Now, the attributes needed for the final result are fetched and then
					//checks are performed to see if they are there in the plan generated so fat
					Set<Attribute> neededAttrs = getResAttrs(plan,p);
					List<Attribute> availableAttrs = bp.getOutput().getAttributes();

					if (neededAttrs.size() == availableAttrs.size() && availableAttrs.containsAll(neededAttrs)) {		//if we have everything
						opFresh.add(bp);
					} else {		//if some vital attributes are yet to be added
						List<Attribute> attrsToKeep = availableAttrs.stream().filter(attr -> neededAttrs.contains(attr)).collect(Collectors.toList());
						if (attrsToKeep.size() == 0) {
							opFresh.add(bp);		//if nothing left to keep then just stick to the progress so far
						}
						else {		//project generated for the missing attributes
							Project tempProj = new Project(bp, attrsToKeep);
							tempProj.accept(estimator);
							opFresh.add(tempProj);
						}
					}
				}
				//In case of multiple operators, products are generated
				//The following loop clubs the first two operators and adds it back until only one is left
				//This is then taken as the final plan and the code goes on to cost comparison
				while (opFresh.size() >= 2) {
					Operator oper = opFresh.get(0);
					Operator oper1 = opFresh.get(1);
					Operator product = new Product(oper, oper1);
					product.accept(estimator);
					opFresh.remove(oper);
					opFresh.remove(oper1);
					opFresh.add(product);
				}
				bp = opFresh.get(0);
				bestP = bp;
			}
			Integer costEstimate = estimator.getEstimate(bestP);
			//Comparing with the current best plan
			if (costEstimate < costLow) {
				planOptimal = bestP;
				costLow = costEstimate;
			}
		}
		//System.out.println("Cost hahahahahahah");
		//System.out.println(costLow);
		return planOptimal;
	}


	//This method returns a set with the attributes needed for the result, given the sub plan and the predicates
	public static Set<Attribute> getResAttrs(Operator sp,List<Predicate> preds){
		Set<Attribute> necAtt = new HashSet<>();
		Predicate tempPred = null;
		//The loop below adds in the left and right attributes of the predicate to the resulting set, if they are not null
		Iterator<Predicate> iterPred1 = preds.iterator();
		while(iterPred1.hasNext()){
			tempPred = iterPred1.next();
			if (tempPred.getLeftAttribute() != null)
				necAtt.add(tempPred.getLeftAttribute());
			if (tempPred.getRightAttribute() != null)
				necAtt.add(tempPred.getRightAttribute());
		}
		//If the sub-plan is an instance of the project class, then the involved attributes are added to 'necAtt'
		//as they will be needed for the final output
		if (sp instanceof Project) {
			necAtt.addAll(((Project) sp).getAttributes());
			return necAtt;
		}
		else {
			return necAtt;
		}
	}

	//Takes the list of predicates as input, builds a list of possible predicate combinations and returns the same
	public static List<List<Predicate>> predCombinations(List<Predicate> attrs) {
		if (attrs.size() == 0) {		//if there is not predicate, return empty list
			List<List<Predicate>> ecmbns = new ArrayList<List<Predicate>>();
			ecmbns.add(new ArrayList<>());
			return ecmbns;
		}
		// there are multiple elements, remove the first
		Predicate head = attrs.remove(0);
		List<List<Predicate>> cmbnsResult = new ArrayList<List<Predicate>>();
		List<List<Predicate>> cmbns = predCombinations(attrs);
		//The loop below iterates over the combinations of predicates and adds them to the result list
		Iterator<List<Predicate>> iterPred = cmbns.iterator();
		List<Predicate> subCombs = null;
		while (iterPred.hasNext()){
			subCombs = iterPred.next();
			int size = 0;
			while (size <= subCombs.size()) {
				List<Predicate> Comb = new ArrayList<Predicate>(subCombs);
				Comb.add(size, head);
				cmbnsResult.add(Comb);
				size++;
			}
		}
		//System.out.println("Final output....we done");
		//System.out.println(cmbnsResult);
		return cmbnsResult;
	}
}