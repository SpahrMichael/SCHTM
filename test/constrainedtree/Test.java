package constrainedtree;


import beast.core.parameter.RealParameter;
import org.junit.Assert;
import org.junit.BeforeClass;


import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class Test {


    static PatientDynamics c = new PatientDynamics();
    PatientDynamics l = new PatientDynamics();


    public Test() {
    }

    static int N_DEMES;
    static int N_EVENTS;
    static int N_CHANGES_EVENT;
    static int N_INTERVAL;
    static int N_STATES;


    @BeforeClass
    public static void createTestCvs() throws IOException {

        List<List<String>> rows = Arrays.asList(
                Arrays.asList("001", "001", "Adam", "A", "A","Admission", "a", "2019-03-09"),
                Arrays.asList("001", "001", "Adam", "A", "A","Discharge", "a", "2020-03-09"),
                Arrays.asList("001", "001", "Eve", "A", "A","Admission", "a", "2019-03-10"),
                Arrays.asList("001", "001", "Eve", "A", "A","Discharge", "a", "2019-03-11"),
                Arrays.asList("001", "001", "God", "A", "A","Admission", "b", "1992-12-08"),
//                Arrays.asList("001", "001", "God", "B", "","Admission", "", "2020-03-09"),//add only one trait and keep the other two
                Arrays.asList("001", "001", "God", "A", "A","Discharge", "b", "2020-03-09"),
                Arrays.asList("", "", "God", "", "","Nothing", "", "2019-02-13")
        );

        N_DEMES = (int) rows.stream().map(p -> p.get(2)).distinct().count();//3
        N_EVENTS = rows.size();//7
        N_CHANGES_EVENT = (int) rows.stream().filter(r ->
                r.get(5).equals("Discharge") || r.get(5).equals("Admission")).map(p -> p.get(7)).count();//6
        N_INTERVAL = (int) rows.stream().filter(r ->
                r.get(5).equals("Discharge") || r.get(5).equals("Admission")).map(p -> p.get(7)).distinct().count();//5
        N_STATES = 3;



        FileWriter writer = new FileWriter("testFile");
        writer.append("H1");
        writer.append(',');
        writer.append("H2");
        writer.append(',');
        writer.append("Patient_ID");
        writer.append(',');
        writer.append("Trait1");
        writer.append(',');
        writer.append("Trait2");
        writer.append(',');
        writer.append("Event");
        writer.append(',');
        writer.append("Trait3");
        writer.append(',');
        writer.append("Date");
        writer.append('\n');

        for (List<String> row : rows) {
            writer.append(String.join(",", row));
            writer.append("\n");
        }
        writer.flush();
        writer.close();
        RealParameter Ne = new RealParameter("1.0");
        c.initByName("Ne", Ne,
                "mCoeff", Ne,
                "dimension", 3,
                "csvFileName", "testFile",
                "fieldName", "Patient_ID,Date,Event,Trait1,Trait2,Trait3",
                "inOutName", "Admission,Discharge");
    }



    public void csvWithWeirdEvent(String fileName) throws IOException {
        List<List<String>> rows = Arrays.asList(
                Arrays.asList("PatientID", "Event", "StatusO", "Status2", "Date", "UselessTrait"),
                Arrays.asList( "G", "+", "R", "r", "2019-10-11","a"),
                Arrays.asList( "E", "+", "R", "r", "2019-10-11","b"),
                Arrays.asList( "E", "-", "", "r", "2019-11-15","c"),
                Arrays.asList( "E", "-", "R", "", "2019-12-02","d"),
                Arrays.asList("G", "+", "R", "r", "2019-12-04","e"),
                Arrays.asList( "G", "-", "ϱ", "r", "2019-12-15","f"),
                Arrays.asList( "E", "+", "R", "r", "2019-12-23","g"),
                Arrays.asList( "B", "-", "R","r", "2019-12-29","∀"));
        FileWriter writer = new FileWriter(fileName);
        for (List<String> row : rows) {
            writer.append(String.join(",", row));
            writer.append("\n");
        }
        writer.flush();
        writer.close();

    }

    // #####################    TEST   #########################


    @org.junit.Test
    public void getPatientListTest() throws IOException {

//        createTestCvs("testFile");

        List<String> expectedPatientList = new ArrayList<>();
        expectedPatientList.add("Adam");
        expectedPatientList.add("Eve");
        expectedPatientList.add("God");

        List<String> patients = c.getPatientList("testFile");
        System.out.println(patients);

        assertEquals(N_DEMES, patients.size());
        assertEquals(expectedPatientList, patients);
    }

    @org.junit.Test
    public void getListPatientChangeTest() throws IOException {

//        createTestCvs("testFile");

        List<List<String>> patientsChange = c.getListPatientChange("testFile");
        System.out.println(patientsChange);

        assertEquals(N_CHANGES_EVENT, patientsChange.size());
        assertEquals(6,patientsChange.get(0).size());
        assertEquals("0.0", patientsChange.get(0).get(1));
        assertEquals("God", patientsChange.get(0).get(0));
    }

    @org.junit.Test
    public void getIntervalTimesTest(){

        List<Double> timeOfEvents = new ArrayList<>();
        timeOfEvents.add(0.0);
        timeOfEvents.add(0.111019);
        timeOfEvents.add(0.291219);
        timeOfEvents.add(7.0);
        timeOfEvents.add(42.0);

        double[] actualIntervalTime = c.getIntervalTimes(timeOfEvents);
        System.out.println(Arrays.toString(actualIntervalTime));

        assertEquals(timeOfEvents.size() - 1, actualIntervalTime.length);
        assertEquals(timeOfEvents.get(1), actualIntervalTime[0], 0.00001);
        assertEquals(timeOfEvents.get(timeOfEvents.size()-1) - timeOfEvents.get(timeOfEvents.
                size()-2), actualIntervalTime[actualIntervalTime.length-1], 0.00001);
    }


    @org.junit.Test
    public void getStateInfoTest() throws IOException {
//        createTestCvs("testFile");


        boolean[][] expected = new boolean[N_DEMES*N_DEMES][N_STATES];


        List<String> patients_list = c.getPatientList("testFile");
        List<List<String>> patientState = c.initPatientsState(patients_list.size(), patients_list);
        boolean[][] actualStateInfo = c.getStateInfo(patientState, N_STATES);

        Arrays.asList(actualStateInfo).forEach(s -> System.out.println(Arrays.toString(s)));

        Assert.assertEquals(expected.length, actualStateInfo.length);
        Assert.assertEquals(expected[0].length, actualStateInfo[0].length);
    }


    @org.junit.Test
    public void setStateInfoTensorTest() throws IOException {

//        createTestCvs("testFile");

        List<String> patients_list = c.getPatientList("testFile");
        List<List<String>> patientState = c.initPatientsState(3, patients_list);
        List<List<String>> psc = c.getListPatientChange("testFile");
        int n_deme = patients_list.size();
        int n_event = psc.size();
        int n_changes = (int) psc.stream().map(p -> Double.valueOf(p.get(1))).distinct().count();
        int n_states = 3;
        boolean[][][] actualTensor = c.setStateInfoTensor(n_changes, n_deme, n_states, psc, patientState);

        System.out.println(Arrays.deepToString(actualTensor));


        assertEquals(N_CHANGES_EVENT, n_event);
        assertEquals(N_INTERVAL, n_changes);
        assertEquals(N_INTERVAL, actualTensor.length);
        assertEquals(N_DEMES*N_DEMES, actualTensor[0].length);
        assertEquals(N_STATES, actualTensor[0][0].length);


        //Adam and Eve not present yet
        assertFalse(actualTensor[0][6][2]);
        assertFalse(actualTensor[0][7][2]);

        //reflexive when trait set
        for (int k = 0; k < N_STATES; ++k){
            assertTrue(actualTensor[0][8][k]);//God here and God.Trait1 == God.Trait1
            assertTrue(actualTensor[1][0][k]);//Adam in
            assertTrue(actualTensor[2][4][k]);//Eve
        }

        //symmetric
        for(int i = 0; i < N_INTERVAL; ++i) {
            for (int k = 0; k < N_STATES; ++k) {
                assertEquals(actualTensor[i][1][k], actualTensor[i][3][k]);
                assertEquals(actualTensor[i][2][k], actualTensor[i][6][k]);
                assertEquals(actualTensor[i][5][k], actualTensor[i][7][k]);

            }
        }

    }

    @org.junit.Test
    public void varTraitTest() throws IOException {//test addition/removal of only 1 trait at the time, removal of wrong state
        csvWithWeirdEvent("file");
        RealParameter Ne = new RealParameter("1.0");
        l.initByName("Ne", Ne,
                "mCoeff", Ne,
                "dimension", 3,
                "csvFileName", "file",
                "fieldName", "PatientID,Date,Event,StatusO,Status2",
                "inOutName", "+,-");
        List<String> pl = l.getPatientList("file");
        List<List<String>> lpc = l.getListPatientChange("file");
        List<List<String>> patientState = l.initPatientsState(3,pl);
        boolean[][][] states = l.setStateInfoTensor(7, pl.size(), 2, lpc, patientState);

        System.out.println(lpc.get(0));
        for(int i = 0; i < 7; ++i){
            assertTrue(states[i][8][0]);
            assertFalse(states[i][0][0]);
            assertFalse(states[i][1][0]);
            assertFalse(states[i][2][0]);
            assertEquals(states[i][5][0],states[i][7][0]);
            System.out.println(lpc.get(i+1));
            System.out.println(Arrays.deepToString(states[i]));
        }
    }


    //Exceptions thrown because of XML error

    //csv error
    @org.junit.Test(expected = java.lang.Exception.class)
    public void missingCsvShouldThrowException(){
        RealParameter Ne = new RealParameter("1.0");
        l.initByName("Ne", Ne,
                "mCoeff", Ne,
                "dimension", 3,
                "csvFileName", "missing",
                "fieldName", "Patient_ID,Date,Event,Trait1,Trait2",
                "inOutName", "Admission,Discharge");
    }

    //fieldName error
    @org.junit.Test(expected = java.lang.Exception.class)
    public void wrongFieldNotInHeaderShouldThrowException(){
        RealParameter Ne = new RealParameter("1.0");
        l.initByName("Ne", Ne,
                "mCoeff", Ne,
                "dimension", 3,
                "csvFileName", "testFile",
                "fieldName", "PatientID,Date,Event,Trait1,Trait2",
                "inOutName", "Admission,Discharge");
    }
    @org.junit.Test(expected = java.lang.Exception.class)
    public void dateFieldNotInSecondPlaceShouldThrowException(){
        RealParameter Ne = new RealParameter("1.0");
        l.initByName("Ne", Ne,
                "mCoeff", Ne,
                "dimension", 3,
                "csvFileName", "testFile",
                "fieldName", "Date,Patient_ID,Event,Trait1,Trait2",
                "inOutName", "Admission,Discharge");
    }
    @org.junit.Test(expected = java.lang.Exception.class)
    public void missingTraitInFieldNameShouldThrowException(){
        RealParameter Ne = new RealParameter("1.0");
        l.initByName("Ne", Ne,
                "mCoeff", Ne,
                "dimension", 3,
                "csvFileName", "testFile",
                "fieldName", "Patient_ID,Date,Event",
                "inOutName", "+,-");
    }

    //inOutName error
    @org.junit.Test(expected = java.lang.Exception.class)
    public void notTwoInOutValueShouldThrowException3(){
        RealParameter Ne = new RealParameter("1.0");
        l.initByName("Ne", Ne,
                "mCoeff", Ne,
                "dimension", 3,
                "csvFileName", "testFile",
                "fieldName", "Patient_ID,Date,Event,Trait1,Trait2",
                "inOutName", "Admission");
    }
    @org.junit.Test(expected = java.lang.Exception.class)
    public void wrongInOutShouldThrowException(){
        RealParameter Ne = new RealParameter("1.0");
        l.initByName("Ne", Ne,
                "mCoeff", Ne,
                "dimension", 3,
                "csvFileName", "testFile",
                "fieldName", "Patient_ID,Date,Event,Trait1,Trait2",
                "inOutName", "+,-");
    }


}
