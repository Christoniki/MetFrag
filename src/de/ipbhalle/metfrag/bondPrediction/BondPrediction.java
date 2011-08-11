	/*
	*
	* Copyright (C) 2009-2010 IPB Halle, Sebastian Wolf
	*
	* Contact: swolf@ipb-halle.de
	*
	* This program is free software: you can redistribute it and/or modify
	* it under the terms of the GNU General Public License as published by
	* the Free Software Foundation, either version 3 of the License, or
	* (at your option) any later version.
	*
	* This program is distributed in the hope that it will be useful,
	* but WITHOUT ANY WARRANTY; without even the implied warranty of
	* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	* GNU General Public License for more details.
	*
	* You should have received a copy of the GNU General Public License
	* along with this program.  If not, see <http://www.gnu.org/licenses/>.
	*
	*/

package de.ipbhalle.metfrag.bondPrediction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openscience.cdk.Atom;
import org.openscience.cdk.Bond;
import org.openscience.cdk.charges.GasteigerMarsiliPartialCharges;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.nonotify.NoNotificationChemObjectBuilder;
import org.openscience.cdk.tools.diff.tree.BondOrderDifference;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

import de.ipbhalle.metfrag.moldynamics.Distance;
import de.ipbhalle.metfrag.tools.Constants;
import de.ipbhalle.metfrag.tools.MoleculeTools;
import de.ipbhalle.metfrag.tools.renderer.StructureRenderer;

public class BondPrediction {
	

		
		private IAtomContainer mol;
		private IAtomContainer molWithAllProtonationSites;
		private Map<String, Double> bondToBondLength;
		private Map<String, Double> bondToBondOrder;
		private Map<String, Double> bondToBondOrderDiff;
		private boolean verbose = false;
		private List<CalculationResult> results= null;
		private List<IBond> aromaticBonds = null;
		private boolean render = false;
		private String mopacMessages = "";
		private String mopacMessagesNeutral = "";
		
		
		/**
		 * Instantiates a new charges class.
		 * 
		 */
		public BondPrediction(List<IBond> aromaticBonds)
		{
			this.bondToBondLength = new HashMap<String, Double>();
			this.bondToBondOrder = new HashMap<String, Double>();
			this.bondToBondOrderDiff = new HashMap<String, Double>();
			this.setResults(new ArrayList<CalculationResult>());
			this.aromaticBonds = aromaticBonds;
		}
		
		
		public void debug(boolean render)
		{
			verbose = true;
			this.render = render;
		}
		
		
		/**
		 * Gets the original molecule after it was processed.
		 * 
		 * @return the original molecule
		 */
		public IAtomContainer getOriginalMolecule()
		{
			return this.mol;
		}
		
		
		/**
		 * Calculate bonds which will most likely break.
		 * It returns a list with bonds.
		 *
		 * @param pathToBabel if a different openbael is to be used! e.g. "/vol/local/bin/"
		 * @param mopacExecuteable the mopac executeable
		 * @param mol the mol
		 * @param FFSteps the fF steps
		 * @param ffMethod the ff method
		 * @param mopacMethod the mopac method
		 * @param mopacRuntime the mopac runtime
		 * @param deleteTemp the delete temp
		 * @return the list< i bond>
		 * @throws Exception the exception
		 */
		public List<String> calculateBondsToBreak(String pathToBabel, String mopacExecuteable, IAtomContainer mol, int FFSteps, String ffMethod, String mopacMethod, Integer mopacRuntime, boolean deleteTemp) throws Exception
		{		
			List<String> bondsToBreak = new ArrayList<String>();
			
			//now optimize the geometry of the neutral molecule
			Mopac mopac = new Mopac();
			Map<String, Double> bondToBondOrderOrig = new HashMap<String, Double>();
			try {	
				//now optimize the structure of the neutral molecue
	    		this.mol = mopac.runOptimization(pathToBabel,mopacExecuteable, mol, FFSteps, true, ffMethod, mopacMethod, mopacRuntime, true, "Neutral", deleteTemp, 0);
	    		if(this.mol == null)
	    		{
	    			this.mopacMessagesNeutral = "ERROR!\nHeat of Formation: " + mopac.getHeatOfFormation() + "\nTime: " + mopac.getTime() + "\nWarning: " + mopac.getWarningMessage() + "\nError: " + mopac.getErrorMessage() + "\n\n";
	    			System.err.println("Was not able to optimize neutral molecule!");
	    			throw new Exception("Error optimizing molecule!");
	    		}
	    		else
	    			this.mopacMessagesNeutral = "Neutral Molecule MOPAC\nHeat of Formation: " + mopac.getHeatOfFormation() + "\nTime: " + mopac.getTime() + "\nWarning: " + mopac.getWarningMessage() + "\nError: " + mopac.getErrorMessage() + "\n\n";

	           	bondToBondOrderOrig = mopac.getBondOrder();
	           	for (IBond bond : this.mol.bonds()) {
					IAtom atom1 = bond.getAtom(0);
					IAtom atom2 = bond.getAtom(1);
					String key = atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-"  +	atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1);
					bond.setProperty(Constants.BONDORDER, bondToBondOrderOrig.get(key));
				}
	    		
	        	GasteigerMarsiliPartialCharges peoe = new GasteigerMarsiliPartialCharges();
//	        	GasteigerPEPEPartialCharges pepe = new GasteigerPEPEPartialCharges();
//	        	AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(this.mol);
//	            AtomContainerManipulator.convertImplicitToExplicitHydrogens(this.mol);
//	            this.mol = MoleculeTools.moleculeNumbering(this.mol);
	    		peoe.calculateCharges(this.mol);
//		    	pepe.calculateCharges(this.mol);
	    		
	    		if(render)
	    			new StructureRenderer(this.mol, "Neutral");
	    		
	    		
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				//stop optimization
				return null;
			}	
	        

			//now get the atoms with the smallest partial charge
			List<AtomProperty> atomCharges = new ArrayList<AtomProperty>();
			boolean[] atomDone = new boolean[this.mol.getAtomCount()];

			for (IBond bond : this.mol.bonds()) {			
	        	for (IAtom atom : bond.atoms()) {
	        		if(!atomDone[Integer.parseInt(atom.getID())])
	        		{
	        			AtomProperty atomProp = new AtomProperty(atom, atom.getCharge());
	        			atomCharges.add(atomProp);
	        			atomDone[Integer.parseInt(atom.getID())] = true;
	        		}
				}	        	
			}
			
			AtomProperty[] chargesArray = new AtomProperty[atomCharges.size()];
			chargesArray = atomCharges.toArray(chargesArray);
			Arrays.sort(chargesArray);
			
//			for (int i = 0; i < chargesArray.length; i++) {
//				System.out.println(chargesArray[i].getAtom().getSymbol() + " " + chargesArray[i].getCharge());
//			}
			
			IAtomContainer[] molArray = new IAtomContainer[2];
			molArray[0] = this.mol;
			this.molWithAllProtonationSites = (IAtomContainer)mol.clone();
			
			Map<String, Distance> cpd1BondToDistance = new HashMap<String, Distance>();
			Map<String, Distance> cpd2BondToDistance = new HashMap<String, Distance>();
			
			//thats the smallest value for charge
			Double minCharge = chargesArray[0].getCharge();
			
			//now add to every candidate atom a hydrogen and calculate the charges again
			for (int i = 0; i < chargesArray.length; i++) {
				
				IAtomContainer protonatedMol = null;
						
				
				//only atoms with partial charge < 0
				if(chargesArray[i].getCharge() > 0)
					continue;			
				else if(!chargesArray[i].getAtom().getSymbol().equals("C") && !chargesArray[i].getAtom().getSymbol().equals("H"))
				{				
					//now add hydrogen atom
					protonatedMol = (IAtomContainer) this.mol.clone();
					
					if(verbose)
						System.out.println("Protonation of atom: " + chargesArray[i].getAtom().getSymbol()  + (Integer.parseInt(chargesArray[i].getAtom().getID()) + 1));
					
					IAtom hydrogenAtom = new Atom("H");
					hydrogenAtom.setID(Integer.toString(this.mol.getAtomCount()));
					IBond hydrogenBond = new Bond(AtomContainerManipulator.getAtomById(protonatedMol, chargesArray[i].getAtom().getID()), hydrogenAtom);
					hydrogenBond.setID(Integer.toString(this.mol.getBondCount()));
					protonatedMol.addAtom(hydrogenAtom);
					protonatedMol.addBond(hydrogenBond);
					AtomContainerManipulator.getAtomById(protonatedMol, chargesArray[i].getAtom().getID()).setFormalCharge(1);
					
					
					IAtom hydrogenAtom1 = new Atom("H");
					IBond hydrogenBond1 = new Bond(AtomContainerManipulator.getAtomById(protonatedMol, chargesArray[i].getAtom().getID()), hydrogenAtom);
					this.molWithAllProtonationSites.addAtom(hydrogenAtom1);
					this.molWithAllProtonationSites.addBond(hydrogenBond1);
					AtomContainerManipulator.getAtomById(molWithAllProtonationSites, chargesArray[i].getAtom().getID()).setFormalCharge(1);
					
//					AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(protonatedMol);
//		            AtomContainerManipulator.convertImplicitToExplicitHydrogens(protonatedMol);
					
//					//now set the charge of the atom...separate atom container
					IAtomContainer outputStructure = (IAtomContainer) protonatedMol.clone();
					outputStructure = AtomContainerManipulator.removeHydrogens(outputStructure);
					AtomContainerManipulator.getAtomById(outputStructure, chargesArray[i].getAtom().getID()).setFormalCharge(1);
					AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(outputStructure);
					AtomContainerManipulator.convertImplicitToExplicitHydrogens(outputStructure);
					
					
//					IBond hydrogenBondAll = new Bond(AtomContainerManipulator.getAtomById(this.molWithAllProtonationSites, chargesArray[i].getAtom().getID()), hydrogenAtom);
//					this.molWithAllProtonationSites.addAtom(hydrogenAtom);
//					this.molWithAllProtonationSites.addBond(hydrogenBondAll);
					
					
		            
//		            Render.Draw(this.mol, "original");
		            if(render)
		            	new StructureRenderer(protonatedMol, "protonated");
		            
		            
		            //optimize the geometry of the protonated molecule
		            
		            try
		            {
		            	protonatedMol = mopac.runOptimization(pathToBabel, mopacExecuteable, protonatedMol, FFSteps, true, ffMethod, mopacMethod, mopacRuntime, false, chargesArray[i].getAtom().getSymbol()  + (Integer.parseInt(chargesArray[i].getAtom().getID()) + 1), deleteTemp, 1);
		            	Map<String, Double> bondToBondOrderProtonated = new HashMap<String, Double>();
		            	bondToBondOrderProtonated = mopac.getBondOrder();
		            	
		            	Map<String, Double> bondToBondOrderProtonatedDiff = new HashMap<String, Double>();
		            	bondToBondOrderProtonatedDiff = compareBondOrders(this.mol, bondToBondOrderProtonated);

		            	//something went wrong during optimization
		            	if(protonatedMol == null)
		            	{
		            		this.mopacMessages = "ERROR after protonation! Atom " + chargesArray[i].getAtom().getSymbol()  + (Integer.parseInt(chargesArray[i].getAtom().getID()) + 1) + "\nHeat of Formation: " + mopac.getHeatOfFormation() + "\nTime: " + mopac.getTime() + "\nWarning: " + mopac.getWarningMessage() + "\nError: " + mopac.getErrorMessage() + "\n\n";
		            		throw new Exception("MOPAC did not finish or error occured!");
		            	}
		            	else
		            	{
		            		this.mopacMessages = "Protonated Molecule MOPAC Atom " + chargesArray[i].getAtom().getSymbol()  + (Integer.parseInt(chargesArray[i].getAtom().getID()) + 1) + "\nHeat of Formation: " + mopac.getHeatOfFormation() + "\nHeat of Formation: " + mopac.getHeatOfFormation() + "\nTime: " + mopac.getTime() + "\nWarning: " + mopac.getWarningMessage() + "\nError: " + mopac.getErrorMessage() + "\n\n";
		            		protonatedMol.setProperty("HeatOfFormation", mopac.getHeatOfFormation());
		            	}
		            	
		            	
		            	
//			            GasteigerPEPEPartialCharges pepe = new GasteigerPEPEPartialCharges();
//			            pepe.calculateCharges(protonatedMol);
			            GasteigerMarsiliPartialCharges peoe = new GasteigerMarsiliPartialCharges();
			            peoe.calculateCharges(protonatedMol);
			            protonatedMol = MoleculeTools.moleculeNumbering(protonatedMol);
						molArray[1] = protonatedMol;
						
						for (int j = 0; j < 2; j++) {
							
							if(!cpd1BondToDistance.isEmpty() && j == 0)
								continue;					
							

							//now compare to the original one
							for (IBond bond : molArray[j].bonds()) {
					        	IAtom atom1 = null;
					        	IAtom atom2 = null;
					        	for (IAtom atom : bond.atoms()) {
					        		if(atom1 == null)
					        			atom1 = atom;
					        		else
					        			atom2 = atom;
					        		
								}

					        	double distance = atom1.getPoint3d().distance(atom2.getPoint3d());
					        	String bondAtomString = atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-" + atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1);
					        	
					        	if(verbose)
					        	{
					        		if(j == 0)
					        			System.out.println("Neutral: " + atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "(" + atom1.getCharge()  + ") -"  + atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1) + "(" + atom2.getCharge()  + ") -" + "\t:" + distance + " BO: " + bondToBondOrderOrig.get(bondAtomString));
					        		else
					        			System.out.println("Protonated: " + atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "(" + atom1.getCharge()  + ") -"  +	atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1) + "(" + atom2.getCharge()  + ") -" + "\t:" + distance + " BO: " + bondToBondOrderProtonated.get(bondAtomString));
					        	}
					        	
					        	Distance dist = null;
					        	

					        	//if the protonated atoms was a terminal one with a oxygen double bond the diff. of the partial charges is set to 0			        	
					        	List<IBond> bondsTemp = molArray[j].getConnectedBondsList(atom1);
								boolean doubleBond = false;
								for (IBond iBond : bondsTemp) {
									if(iBond.getOrder().equals(IBond.Order.DOUBLE))
										doubleBond = true;
								}
					        	
								
								if(atom1.getSymbol().equals("H") || atom2.getSymbol().equals("H"))
									continue;
								
								//give penalty for aromatic rings...they usually don't split...also assume the aromatic rings for the protonated molecule
								if(this.aromaticBonds.contains(bond) || this.aromaticBonds.contains(this.mol.getBond(AtomContainerManipulator.getAtomById(this.mol, atom1.getID()), AtomContainerManipulator.getAtomById(this.mol, atom2.getID()))))
								{
									dist = new Distance(atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-" + atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1), 0.0, bond.getID(), atom1.getID(), atom2.getID(), true);
								}
								//penalty for double bond to terminal oxygen
								else if(atom1.getSymbol().equals("O") && chargesArray[i].getAtom().getID().equals(atom1.getID()) && doubleBond)
								{
									dist = new Distance(atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-" + atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1), 0.0, bond.getID(), atom1.getID(), atom2.getID(), true);
								}
								//penalty for double bond to terminal oxygen
								else if(atom2.getSymbol().equals("O") && chargesArray[i].getAtom().getID().equals(atom2.getID()) && doubleBond)
								{
									dist = new Distance(atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-" + atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1), 0.0, bond.getID(), atom1.getID(), atom2.getID(), true);
								}
								else 
								{
									dist = new Distance(atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-" + atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1), distance, bond.getID(), atom1.getID(), atom2.getID(), false);
								}
								String key = atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-"  +	atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1);
					        	//original molecule
					        	if(j == 0 && !cpd1BondToDistance.containsKey(dist))
					        		cpd1BondToDistance.put(key, dist);
//						        		cpd1BondToDistance.add(new Distance(atom1.getSymbol() + "-" + atom2.getSymbol(), atom1.getPoint2d().distance(atom2.getPoint2d())));
					        	else if(!cpd2BondToDistance.containsKey(dist))
					        		cpd2BondToDistance.put(key, dist);
//						        		cpd2BondToDistance.add(new Distance(atom1.getSymbol() + "-" + atom2.getSymbol(), atom1.getPoint2d().distance(atom2.getPoint2d())));
							}
						}
		       				
						//now compare the results
						String tempResult = "";
						for (String bondID : cpd1BondToDistance.keySet()) {
	//					for (int i1 = 0; i1 < cpd1BondToDistance.size(); i1++) {
							Double dist = -999.9;
							
							if(cpd1BondToDistance.get(bondID) == null)
								System.err.println("Bond ID (" + bondID + ") is null in cpd1");
							else if(cpd2BondToDistance.get(bondID) == null)
								System.err.println("Bond ID (" + bondID + ") is null in cpd2");
							else if(cpd1BondToDistance.get(bondID).getBond().equals(cpd2BondToDistance.get(bondID).getBond()))
							{
								dist = (cpd2BondToDistance.get(bondID).getBondLength() - cpd1BondToDistance.get(bondID).getBondLength());
																								
								//now normalize the partial charge differences according to the partial charges
								
								//now normalize the current charge
								Double normalizedPartialChargeOfProtonizedAtom = (chargesArray[i].getCharge() / minCharge);
	
//								double distRound = Math.round((dist * normalizedPartialChargeOfProtonizedAtom) * 1000.0)/1000.0;
								double distRound = Math.round((dist) * 1000.0)/1000.0;
								//set the bond length change
								protonatedMol.getBond(AtomContainerManipulator.getAtomById(protonatedMol, cpd2BondToDistance.get(bondID).getAtom1ID()), AtomContainerManipulator.getAtomById(protonatedMol, cpd2BondToDistance.get(bondID).getAtom2ID()))
									.setProperty(Constants.BONDLENGTHCHANGE, distRound);
								//now save only the maximum bond length change...
								bondToBondLength = saveMaximum(bondToBondLength, cpd1BondToDistance.get(bondID).getBondID(), distRound);
								
								Double currentBondOrder = 0.0;
								Double currentBondOrderDiff = 0.0;
								if(cpd2BondToDistance.get(bondID).isInAromaticRing())
								{
									//set bond order
									protonatedMol.getBond(AtomContainerManipulator.getAtomById(protonatedMol, cpd2BondToDistance.get(bondID).getAtom1ID()), AtomContainerManipulator.getAtomById(protonatedMol, cpd2BondToDistance.get(bondID).getAtom2ID()))
										.setProperty(Constants.BONDORDER, 2.0);									
									currentBondOrder = 2.0;
									currentBondOrderDiff = 0.0;
									this.bondToBondOrder = saveMinimum(bondToBondOrder, cpd1BondToDistance.get(bondID).getBondID(), 2.0);
									this.bondToBondOrderDiff = saveMaximum(bondToBondOrderDiff, cpd1BondToDistance.get(bondID).getBondID(), 0.0);
								}
								else
								{
									//set bond order
									currentBondOrder = bondToBondOrderProtonated.get(bondID);
									currentBondOrderDiff = bondToBondOrderProtonatedDiff.get(bondID);
									protonatedMol.getBond(AtomContainerManipulator.getAtomById(protonatedMol, cpd2BondToDistance.get(bondID).getAtom1ID()), AtomContainerManipulator.getAtomById(protonatedMol, cpd2BondToDistance.get(bondID).getAtom2ID()))
										.setProperty(Constants.BONDORDER, currentBondOrder);									
									this.bondToBondOrder = saveMinimum(bondToBondOrder, cpd1BondToDistance.get(bondID).getBondID(), bondToBondOrderProtonated.get(bondID));
									this.bondToBondOrderDiff = saveMaximum(bondToBondOrderDiff, cpd1BondToDistance.get(bondID).getBondID(), bondToBondOrderProtonatedDiff.get(bondID));
								}
																
								
								
								
	//								tempResult += cpd1BondToDistance.get(i1).getBond() + " " + cpd1BondToDistance.get(i1).getBondLength() + " " + cpd2BondToDistance.get(i1 + offset).getBondLength() + ": " + distRound + "\n";
								tempResult += cpd1BondToDistance.get(bondID).getBond() +  "\t" + distRound + "\t" + currentBondOrder + "\t" + currentBondOrderDiff +"\n";
								
							}
							else
							{
								System.err.println("No bond pair found!");
	//							dist = (cpd2BondToDistance.get(i1 + offset).getBondLength() - cpd1BondToDistance.get(i1).getBondLength());
							}				
						}
						
						if(verbose)
							System.out.println(tempResult);
						
						
						for (IBond bond : protonatedMol.bonds()) {
							
							if(bondToBondLength.get(bond.getID()) == null)
							{
								bond.setProperty(Constants.BONDLENGTHCHANGE, 0.0);
								bond.setProperty(Constants.BONDORDER, 2.0);
								bond.setProperty(Constants.BONDORDERDIFF, 0.0);
							}
							else
							{
								bond.setProperty(Constants.BONDLENGTHCHANGE, bondToBondLength.get(bond.getID()));
								String bondString = bond.getAtom(0).getSymbol() + (Integer.parseInt(bond.getAtom(0).getID()) + 1) + "-" + bond.getAtom(1).getSymbol() + (Integer.parseInt(bond.getAtom(1).getID()) + 1);
								bond.setProperty(Constants.BONDORDER, bondToBondOrderProtonated.get(bondString));
								bond.setProperty(Constants.BONDORDERDIFF, bondToBondOrderProtonatedDiff.get(bondString));
							}
							
						}
						
						
						IAtomContainer molWithoutProton = (IAtomContainer) this.mol.clone();
						molWithoutProton.setProperty("HeatOfFormation", mopac.getHeatOfFormation());
						for (IBond bond : molWithoutProton.bonds()) {
							
							if(bondToBondLength.get(bond.getID()) == null)
							{
								bond.setProperty(Constants.BONDLENGTHCHANGE, 0.0);
								bond.setProperty(Constants.BONDORDER, 2.0);
								bond.setProperty(Constants.BONDORDERDIFF, 0.0);
							}
							else
							{
								bond.setProperty(Constants.BONDLENGTHCHANGE, bondToBondLength.get(bond.getID()));
								String bondString = bond.getAtom(0).getSymbol() + (Integer.parseInt(bond.getAtom(0).getID()) + 1) + "-" + bond.getAtom(1).getSymbol() + (Integer.parseInt(bond.getAtom(1).getID()) + 1);
								bond.setProperty(Constants.BONDORDER, bondToBondOrderProtonated.get(bondString));
								bond.setProperty(Constants.BONDORDERDIFF, bondToBondOrderProtonatedDiff.get(bondString));
							}
							
						}
												
						this.results.add(new CalculationResult(molWithoutProton, protonatedMol, outputStructure, tempResult, chargesArray[i].getAtom().getSymbol()  + (Integer.parseInt(chargesArray[i].getAtom().getID()) + 1), this.mopacMessages));

//						for (String string : notMatched) {
//							System.out.println(string);
//						}
					
		            }
		            catch(Exception e)
		            {
		            	System.out.println("Error in protonized molecule optimization! Skipped it");
		            	e.printStackTrace();
		            }					
				}
			}
			
			
			
			//now use the original molecule to get all the bond ids and write the partial changes into var
			for (IBond bond : molArray[0].bonds()) {
//				System.out.println(bond.getID() + " " + bond.getAtom(0).getSymbol() + "-" + bond.getAtom(1).getSymbol() + " " + bondToBondLength.get(bond.getID()));
				
				//if it is C-C check for double or triple bond order
				if(!isCandidateBond(bond))
					continue;
				
				if(bondToBondLength.get(bond.getID()) > 0)
					bondsToBreak.add(bond.getID());				
			}
			
//			for (String bondID : bondsToBreak) {
//				System.out.println(bondID);
//			}

			
			//now add the complete combined result in front of the list
			
			IAtomContainer molOriginal = (IAtomContainer)this.mol.clone();
			
			String combinedResults = "";
			for (IBond bond : molOriginal.bonds()) {
				
				combinedResults += bond.getAtom(0).getSymbol() + (Integer.parseInt(bond.getAtom(0).getID()) + 1) + "-" + bond.getAtom(1).getSymbol() + (Integer.parseInt(bond.getAtom(1).getID()) + 1) + "\t" + bondToBondLength.get(bond.getID()) + "\t" + bondToBondOrder.get(bond.getID()) + "\t" + bondToBondOrderDiff.get(bond.getID()) + "\n";
				if(bondToBondLength.get(bond.getID()) == null)
				{
					bond.setProperty(Constants.BONDLENGTHCHANGE, 0.0);
					bond.setProperty(Constants.BONDORDER, 2.0);
					bond.setProperty(Constants.BONDORDERDIFF, 0.0);
					
				}
				else
				{
					bond.setProperty(Constants.BONDLENGTHCHANGE, bondToBondLength.get(bond.getID()));
					bond.setProperty(Constants.BONDORDER, this.bondToBondOrder.get(bond.getID()));
					bond.setProperty(Constants.BONDORDERDIFF, this.bondToBondOrderDiff.get(bond.getID()));
				}
				
			}
//			for (String bond : bondToBondLength.keySet()) {
//				combinedResults += AtomContainerManipulator.g(this.mol, bond) bond + " " + bondToBondLength.get(bond);
//			}
			
//			AtomContainerManipulator.convertImplicitToExplicitHydrogens(this.molWithAllProtonationSites);
//	        AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(this.molWithAllProtonationSites);
//	        this.molWithAllProtonationSites = MoleculeTools.moleculeNumbering(this.molWithAllProtonationSites);
			
			this.results.add(0, new CalculationResult(molOriginal, this.mol, this.molWithAllProtonationSites, combinedResults, "Combined", this.mopacMessagesNeutral));
			
			
			return bondsToBreak;
	            
		}
		
		
		/**
		 * Compare bond orders.
		 *
		 * @param source the source
		 * @param candidate the candidate
		 * @return the string
		 */
		public static String compareBondOrdersString(IAtomContainer source, IAtomContainer candidate)
		{
			String ret = "";
			Map<String, Double> sourceBondMap = new HashMap<String, Double>();
			for (IBond bond : source.bonds()) {
				IAtom atom1 = bond.getAtom(0);
				IAtom atom2 = bond.getAtom(1);
				String key = atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-"  +	atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1);
				sourceBondMap.put(key, (Double)bond.getProperty(Constants.BONDORDER));
			}
			
			Map<String, Double> candidateBondMap = new HashMap<String, Double>();
			for (IBond bond : candidate.bonds()) {
				IAtom atom1 = bond.getAtom(0);
				IAtom atom2 = bond.getAtom(1);
				String key = atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-"  +	atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1);
				candidateBondMap.put(key, (Double)bond.getProperty(Constants.BONDORDER));
			}
			
			for (IBond bond : source.bonds()) {
				IAtom atom1 = bond.getAtom(0);
				IAtom atom2 = bond.getAtom(1);
				String key = atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-"  +	atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1);
				if(!key.contains("H"))
					ret += key + ":" + (Math.round((sourceBondMap.get(key) - candidateBondMap.get(key)) * 1000)/1000.0) + "\n";
			}
			
			return ret;
		}
		
		
		/**
		 * Compare bond orders.
		 *
		 * @param source the source
		 * @param candidate the candidate
		 * @return the map
		 */
		public static Map<String, Double> compareBondOrders(IAtomContainer source, Map<String, Double> candidateBondMap)
		{
			Map<String, Double> ret = new HashMap<String, Double>();
			Map<String, Double> sourceBondMap = new HashMap<String, Double>();
			for (IBond bond : source.bonds()) {
				IAtom atom1 = bond.getAtom(0);
				IAtom atom2 = bond.getAtom(1);
				String key = atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-"  +	atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1);
				sourceBondMap.put(key, (Double)bond.getProperty(Constants.BONDORDER));
			}
			
			for (IBond bond : source.bonds()) {
				IAtom atom1 = bond.getAtom(0);
				IAtom atom2 = bond.getAtom(1);
				String key = atom1.getSymbol() + (Integer.parseInt(atom1.getID()) + 1) + "-"  +	atom2.getSymbol() + (Integer.parseInt(atom2.getID()) + 1);
				if(!key.contains("H"))
					ret.put(key, (Math.round((sourceBondMap.get(key) - candidateBondMap.get(key)) * 1000)/1000.0));
			}
			
			return ret;
		}
		
		
		
		private Map<String, Double> saveMaximum(Map<String, Double> bondToBondLength, String bondID, Double dist)
		{
			if(bondToBondLength.get(bondID) == null)
				bondToBondLength.put(bondID, dist);
			else if(bondToBondLength.get(bondID) < dist)
				bondToBondLength.put(bondID, dist);
			
			return bondToBondLength;
		}
		
		private Map<String, Double> saveMinimum(Map<String, Double> bondToBondLength, String bondID, Double dist)
		{
			if(bondToBondLength.get(bondID) == null)
				bondToBondLength.put(bondID, dist);
			else if(bondToBondLength.get(bondID) > dist)
				bondToBondLength.put(bondID, dist);
			
			return bondToBondLength;
		}
		
		
		/**
		 * Checks if it is candidate bond. In a C-C bond it must be a double or
		 * triple bond.
		 * 
		 * @return true, if is candidate bond
		 */
		private boolean isCandidateBond(IBond bond)
		{
			boolean check = false;
			//only protonate carbon atom where a double or triple bond is
			if(bond.getAtom(1).getSymbol().equals("C") && bond.getAtom(0).getSymbol().equals("C"))
			{
				if(bond.getOrder().equals(IBond.Order.DOUBLE) || bond.getOrder().equals(IBond.Order.TRIPLE))
					check = true;
			}
			//dont break bonds with hydrogen
			else if(bond.getAtom(1).getSymbol().equals("H") || bond.getAtom(0).getSymbol().equals("H"))
				check = false;
			else
				check = true;
			
			return check;
		}
		
		/**
		 * Gets the bond length for a specified bond ID.
		 * Molecule bonds are numbered using MoleculeTools.moleculeNumbering
		 * 
		 * @param bondID the bond id
		 * 
		 * @return the bond length
		 */
		public Double getBondLength(String bondID)
		{
			return this.bondToBondLength.get(bondID);
		}
		
		
		/**
		 * Gets the bond order for a specified bond ID.
		 * Molecule bonds are numbered using MoleculeTools.moleculeNumbering
		 * 
		 * @param bondID the bond id
		 * 
		 * @return the bond length
		 */
		public Double getBondOrder(String bondID)
		{
			return this.bondToBondOrder.get(bondID);
		}
		
		/**
		 * Gets the bond order diff. for a specified bond ID.
		 * Molecule bonds are numbered using MoleculeTools.moleculeNumbering
		 * 
		 * @param bondID the bond id
		 * 
		 * @return the bond length
		 */
		public Double getBondOrderDiff(String bondID)
		{
			return this.bondToBondOrderDiff.get(bondID);
		}
		
		
		/**
		 * Sets the bond orders.
		 *
		 * @param bondToBondOrder the bond to bond order
		 */
		public void setBondOrder(Map<String, Double> bondToBondOrder)
		{
			this.bondToBondOrder = bondToBondOrder;
		}
		
		
		/**
		 * Sets the bond lengths.
		 *
		 * @param bondToBondLength the bond to bond length
		 */
		public void setBondLength(Map<String, Double> bondToBondLength)
		{
			this.bondToBondLength = bondToBondLength;
		}
		
		
		public void setResults(List<CalculationResult> results) {
			this.results = results;
		}


		public List<CalculationResult> getResults() {
			return results;
		}


		public String getMopacMessages() {
			return mopacMessages;
		}


		public void setMopacMessages(String mopacMessages) {
			this.mopacMessages = mopacMessages;
		}


		public Map<String, Double> getBondToBondOrderDiff() {
			return bondToBondOrderDiff;
		}


		public void setBondToBondOrderDiff(Map<String, Double> bondToBondOrderDiff) {
			this.bondToBondOrderDiff = bondToBondOrderDiff;
		}
		
}
