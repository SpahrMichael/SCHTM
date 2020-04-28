package constrainedtree;


import beast.core.Description;
import beast.core.Input;
import beast.core.Loggable;
import beast.core.parameter.BooleanParameter;
import beast.core.parameter.RealParameter;
import beast.mascot.dynamics.Dynamics;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;


@Description("Varying migration rates and effective population sizes over time.")
public class PatientDynamics extends Dynamics implements Loggable  {

	BooleanParameter indic = new BooleanParameter("true");
	public Input<RealParameter> NeInput = new Input<>("Ne",
		"input of effective population sizes", Input.Validate.REQUIRED);
	public Input<RealParameter> mCoeffIn = new Input<>("mCoeff",
			"coefficients for migration matrix", Input.Validate.REQUIRED);
	public Input<BooleanParameter> indicatorInput = new Input<>("indicator",
			"network indicator", indic);
	public Input<String> dataInput = new Input<>("csvFileName",
			"csv file containing the data", Input.Validate.REQUIRED);
	public Input<String> fieldNameInput = new Input<>("fieldName",
			"list of necessary field names as header of the CSV file", Input.Validate.REQUIRED);//PatientID,Date,TraitChange,Trait1,Trait2,...,TraitN
	public Input<String> inOutNameInput = new Input<>("inOutName",
			"name for in and out for trait change", Input.Validate.REQUIRED);//"Admission","Discharge"



	public PatientDynamics() {
	}


	private boolean[][][] states;
	private double[] intervalTimes;
	private List<String> fieldName;
	private String[] inOutName;
	private int n_states;



	@Override
    public void initAndValidate(){

    	super.initAndValidate();


		fieldName = Arrays.stream(fieldNameInput.get().split(",")).collect(Collectors.toList());
		n_states = fieldName.size() - 3;
		inOutName = inOutNameInput.get().split(",");
		if(inOutName.length != 2){
			throw new RuntimeException("inOutNameInput should contain two values for adding/removing states\n"
			+ "inOutNameInput: " + Arrays.toString(inOutName));
		}
		if(fieldName.size() < 4){
			throw new RuntimeException("fieldNameInput should contain csv header designing the Patient_ID, the Date,"
					+ " the Change Event and at least one trait\n" + "fieldNameInput = " + fieldName);
		}


		List<String> patients_list;
		try {
			patients_list = getPatientList(dataInput.get());
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Error reading the cvs file");
		}
		int n_demes = patients_list.size();

		List<List<String>> psc;
		try {
			psc = getListPatientChange(dataInput.get());
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Error reading the cvs file");
		}

		List<Double> change_times = psc.stream().map(p
				-> Double.valueOf(p.get(1))).distinct().collect(Collectors.toList());
		int n_changes = change_times.size();

		NeInput.get().setDimension(n_demes);
		mCoeffIn.get().setDimension(n_states + 2);//m_trait1,..,m_traitN,m_0,mu
		indicatorInput.get().setDimension(n_states);

		List<List<String>> patientsState = initPatientsState(n_demes, patients_list);
		states = setStateInfoTensor(n_changes, n_demes, n_states, psc, patientsState);
		intervalTimes = getIntervalTimes(change_times);


    	if (dimensionInput.get() < 1)
    		dimensionInput.set(getNrTypes());
    }

    /**
     * Returns the time to the next interval.
     */
    @Override
	public double getInterval(int i) {
		if(i >= intervalTimes.length){
			return Double.POSITIVE_INFINITY;
		}
		return intervalTimes[i];
	}
    
    @Override
	public double [] getIntervals() {
		return intervalTimes;
	}

	public boolean intervalIsDirty(int i){
		boolean intervalIsDirty = false;

		for (int j = 0; j < NeInput.get().getDimension(); ++j)
			if (NeInput.get().isDirty(j))
				intervalIsDirty = true;
		for (int j = 0; j < mCoeffIn.get().getDimension(); ++j)
			if (mCoeffIn.get().isDirty(j))
				intervalIsDirty = true;

		for (int j = 0; j < indicatorInput.get().getDimension(); ++j) {
			if (indicatorInput.get().isDirty(j))
				intervalIsDirty = true;
		}
		return intervalIsDirty;
	}


	@Override
    public double[] getCoalescentRate(int i){

		int n_demes = NeInput.get().getDimension();
		double[] coal = new double[n_demes];
		double Ne = NeInput.get().getValue();
		for (int j = 0; j < n_demes; ++j) {
			coal[j] = 1 / Ne;
		}
		return coal;
    }


	@Override
	public double[] getBackwardsMigration(int l){

		int n = NeInput.get().getDimension();
		double[] m = new double[n * n];
		for(int i = 0; i < n; ++i){
			for(int j = 0; j < n; ++j){
				if(i != j){
					double rate = mCoeffIn.get().getArrayValue(n_states);//base rate
					boolean[] states_ij = states[l][i*n+j];

					for(int k = 0; k < n_states; ++k){
						if(states_ij[k] && indicatorInput.get().getValue(k)){
							rate += mCoeffIn.get().getArrayValue(k);
						}
					}
					m[i*n+j] = rate * mCoeffIn.get().getValue(n_states+1);
				}
			}
		}
		return m;
	}


	@Override
	public int getEpochCount() {return intervalTimes.length;}


	@Override
	public void recalculate() {
	}



	@Override
	public void init(PrintStream out) {

    	out.print("Ne\t");

		for(int i = 0; i < n_states; ++i){
			out.print(String.format("M.m_%s\t", fieldName.get(i+3)));
		}
		out.print("M.m_0\t");
		out.print("M.mu\t");

		for(int i = 0; i < n_states; ++i){
			out.print(String.format("I.%s\t", fieldName.get(i+3)));
		}
	}

	@Override
	public void log(long sample, PrintStream out) {

    	out.print(NeInput.get().getValue() + "\t");

		for(int i = 0; i < mCoeffIn.get().getDimension(); ++i){
			out.print(mCoeffIn.get().getArrayValue(i) + "\t");
		}
		for(int i = 0; i < indicatorInput.get().getDimension(); ++i){
			out.print(indicatorInput.get().getArrayValue(i) + "\t");
		}
	}

	@Override
	public void close(PrintStream out) {
	}



//PRIVATE METHODS


	//Return a list of sorted patient ID
	public List<String> getPatientList(String pathToTimeLineData) throws IOException {
		Reader in = new FileReader(pathToTimeLineData);
		Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
		Set<String> patients = new HashSet<>();
		for (CSVRecord record : records) {
			patients.add(record.get(fieldName.get(0)));
		}
		List<String> patients_list = new ArrayList<>(patients);
		Collections.sort(patients_list);
    	return patients_list;
	}


	//Return a time-sorted list of {Patient_ID,Date,changeType,Trait1,Trait2,Trait3} for every patient change
	public List<List<String>> getListPatientChange(String pathToTimeLineData) throws IOException {
		Reader patient_state_changes = new FileReader(pathToTimeLineData);
		Iterable<CSVRecord> patientStateChanges =
				CSVFormat.RFC4180.withFirstRecordAsHeader().parse(patient_state_changes);

		List<List<String>> psc = new ArrayList<>();

		//get the list
		List<String> row;//row = {PatientID,Date,changeType,Trait1,...,TraitN}
		for(CSVRecord record : patientStateChanges){
			if(record.get(fieldName.get(2)).equals(inOutName[0])){//patient change add a trait
				row  = new ArrayList<>();
				row.add(record.get(fieldName.get(0)));
				row.add(record.get(fieldName.get(1)));
				row.add("+");
				for (int i = 3; i < n_states + 3; ++i){
					row.add(record.get(fieldName.get(i)));
				}
				psc.add(row);
			}
			else if(record.get(fieldName.get(2)).equals(inOutName[1])){//patient change remove a trait
				row  = new ArrayList<>();
				row.add(record.get(fieldName.get(0)));
				row.add(record.get(fieldName.get(1)));
				row.add("-");
				for (int i = 3; i < n_states + 3; ++i){
					row.add(record.get(fieldName.get(i)));
				}
				psc.add(row);
			}
		}
		//order list by date
		Comparator<List<String>> comparator = Comparator.comparing((List<String> o) -> o.get(1));
		psc.sort(comparator);

		//get date difference instead of absolute date
		Period per;
		double time;
		LocalDate mostRecentChange;
		try {
			mostRecentChange = LocalDate.parse(psc.get(0).get(1));
		} catch (IndexOutOfBoundsException e) {
			e.printStackTrace();
			throw new RuntimeException("List of changes is empty, maybe inOutNameInput is incorrect "
					+ "or values in fieldNameInput are incorrect");
		} catch (DateTimeParseException e) {
			e.printStackTrace();
			throw new RuntimeException("Date should be the second value in filedNameInput " + "" +
					"and the correct format in the csv is: YYYY-MM-DD");
		}

		for(List<String> entry : psc){
			per = Period.between(mostRecentChange, LocalDate.parse(entry.get(1)));
			time = (double) per.getDays()/365.25 + (double) per.getMonths()/12 + per.getYears();
			entry.set(1, Double.toString(time));
		}
		return psc;
	}


	//Initialize list of patients state: {PatientID, Trait1=null, ...,TraitN=null}
	public List<List<String>> initPatientsState(int n_demes, List<String> patients_list){

		List<List<String>> patientsState = new ArrayList<>();
		List<String> patient_i;
		for(int i = 0; i < n_demes; ++i){
			String name = patients_list.get(i);
			patient_i = new ArrayList<>();
			patient_i.add(name);
			for (int j = 0; j < n_states; ++j){
				patient_i.add(null);
			}
			patientsState.add(patient_i);
		}
		return patientsState;
	}


	//Return an array with the length of each interval between patient change
	public double[] getIntervalTimes(List<Double> timeOfEvent){

		double[] intervalTimes = new double[timeOfEvent.size() - 1];
		for (int i = 0; i < intervalTimes.length; ++i){
			intervalTimes[i] = timeOfEvent.get(i+1) - timeOfEvent.get(i);
		}
		return intervalTimes;
	}


	//Create the tensor with all states information for every interval
	public boolean[][][] setStateInfoTensor(int n_change, int n_demes, int n_states,
											List<List<String>> psc, List<List<String>> patientsState){

		boolean[][][] stateInfo = new boolean[n_change][n_demes * n_demes][n_states];
		int c = 0;
		double lastTime = 0;
		for(List<String> record : psc) {//record={PatientID,Date,changeType,Trait1,...,TraitN}
			double curTime = Double.parseDouble(record.get(1));

			//find which patient has a change of state
			for (int i = 0; i < n_demes; ++i) {
				if (patientsState.get(i).get(0).equals(record.get(0))){
					List<String> newState = new ArrayList<>();//newState={PatientID,Trait1,...,TraitN}
					newState.add(record.get(0));

					//allow one or more trait change at the time, other trait don't need to be specify
					if(record.get(2).equals("+")){//add a trait
						for(int j = 3; j < n_states+3; ++j){
							if(!record.get(j).equals("")){//if new trait given, add it, otherwise keep old one
								newState.add(record.get(j));
							}
							else{
								newState.add(patientsState.get(i).get(j-2));
							}
						}
					}
					else{//remove a trait
						for(int j = 3; j < n_states+3; ++j){
							if(record.get(j).equals(patientsState.get(i).get(j-2))){//if old trait given, remove it otherwise keep old one
								newState.add(null);
							}
							else{
								newState.add(patientsState.get(i).get(j-2));
							}
						}
					}
					patientsState.set(i, newState);//replace the state for that patient
					break;
				}
			}
			if(curTime != lastTime){//patient change after last change
				c++;
				stateInfo[c] = getStateInfo(patientsState, n_states);
				lastTime = curTime;
			}
			else{//patient change the same day as last one
				stateInfo[c] = getStateInfo(patientsState, n_states);
			}
		}
		return stateInfo;
	}


	//Return trait information for each pair of patient for given patientsState
	public boolean[][] getStateInfo(List<List<String>> patientsState, int n_states) {

		int n_demes = patientsState.size();
		boolean[][] states = new boolean[n_demes*n_demes][n_states];

		for(int j = 0; j < n_demes * n_demes; ++j){
			//state[j] is between p_i and p_j
			List<String> p_i = patientsState.get(j/n_demes);
			List<String> p_j = patientsState.get(j%n_demes);

			for( int i = 0; i < n_states; ++i){
				if (p_i.get(i+1) != null && p_i.get(i+1).equals(p_j.get(i+1))){
					states[j][i]= true;
				}
			}
		}
		return(states);
	}


}