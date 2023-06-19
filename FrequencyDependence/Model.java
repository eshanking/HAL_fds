package FrequencyDependence;

import HAL.lib.CommandLine;
// import onLatticeCA_jar.OnLatticeGrid;
// import onLatticeCA_jar.OnLatticeModel;
// import FrequencyDependence.Grid;

import java.io.File;
// import static HAL.Util.LoadState;

@CommandLine.Command(name = "2-D Agent-Based Model of Frequency-Dependent Tumor Growth",
        mixinStandardHelpOptions = true,
        showDefaultValues = true,
        description = "A 2D on-lattice agent-based model that simulates 2 cell populations growing together with frequency-dependent interactions.")

public class Model implements Runnable {
    Grid model =  new Grid();
    @CommandLine.Option(names = { "-s", "--seed"}, description="Random number seed.") 
    int seed = model.seed;
    // ------------------------- Experimental Setup -------------------------
    @CommandLine.Option(names = { "-xDim", "--xDim"}, description="x-dimension of domain (in lattice sites)") 
    int xDim = model.xDim;
    @CommandLine.Option(names = { "-yDim", "--yDim"}, description="y-dimension of domain (in lattice sites)") 
    int yDim = model.yDim;
    @CommandLine.Option(names = { "-n", "--nReplicates"}, description="Number of replicates.") 
    int nReplicates = model.nReplicates;
    @CommandLine.Option(names = { "-r0", "--initialRadius"}, description="Initial tumor radius") 
    double initRadius = model.initRadius;
    // @CommandLine.Option(names = { "-n0", "--initialSizeProp"}, description="Initial cell density relative to (physical) carrying capacity") 
    // double initialSizeProp = model.initialSizeProp;
    @CommandLine.Option(names = { "-pR", "--pResistant"}, description="Initial resistance fraction in [0,1]") 
    double rFrac = model.initResistantProp;
    // @CommandLine.Option(names = { "-tEnd", "--tEnd"}, description="End time in days.") 
    // double tEnd = model.tEnd;
    // @CommandLine.Option(names = { "-dt", "--dt"}, description="Time step in days.") 
    // double dt = model.dt;
    // @CommandLine.Option(names = { "-treatmentScheduleList", "--treatmentScheduleList"}, description="Treatment schedule in format {{tStart, tEnd, drugConcentration}}.") 
    // String treatmentScheduleList_string;
    // ------------------------- Output - Text -------------------------
    @CommandLine.Option(names = { "--outDir"}, description="Directory which to save output files to.") 
    String outDir = "./tmp/";
    @CommandLine.Option(names = { "--imageOutDir"}, description="Directory which to save images to.") 
    String imageOutDir = model.imageOutDir;
    // ------------------------- Output - Visualisation -------------------------
    @CommandLine.Option(names = { "--imageFrequency"}, description="Frequency at which an image of the tumour is saved. Negative number turns it off.") 
    int imageFrequency = model.imageFrequency;
    @CommandLine.Option(names = { "-visualiseB", "--visualiseB"}, description="Whether or not to show visualization.")
    Boolean visualiseB = model.visualiseB;

    @CommandLine.Option(names = { "--saveModelState"}, description="Whether or not to save the model object at the end of the simulation.") 
    Boolean saveModelState = model.saveModelState;
    
    // ------------------------------------------------------------------------------------------------------------
    /*
     * Main runner function
     */
    public void run(){
        // ------------------------- 1. Setup -------------------------
        // assert !helpRequested; // Print help message if requested
        // if (headless) {System.setProperty("java.awt.headless", "true");} // Make headless if necessary
        Grid myModel;
        double[] paramArr;
        String currSavedModelFileName;
        String outFName;
        // Boolean fromScratch = savedModelFileName==null;
        new File(outDir).mkdirs(); // Set up environment

        // ------------------------- 2. Experimental Setup -------------------------

        // Prepare storage and indexing for each replicate
        int[] replicateIdList = new int[] {seed};
        int replicateId;
        if (nReplicates>1) {
            replicateIdList = new int[nReplicates];
            for (int i=0; i<nReplicates; i++) {replicateIdList[i]=i;}
        }

        // ------------------------------------------------------------------------
        // Main simulation loop.
        // ------------------------------------------------------------------------
        for (int replicateIdx = 0; replicateIdx < nReplicates; replicateIdx++) {
            replicateId = replicateIdList[replicateIdx];
            // currSavedModelFileName = savedModelFileName==null? outDir + "RepId_" + replicateId + ".bin": savedModelFileName;



            // Set up the simulation
            // paramArr = new double[]{divisionRate_S, divisionRate_R, movementRate_S, movementRate_R,
            //         deathRate_S, deathRate_R, drugEffect_div_S, drugEffect_div_R};
            myModel = new Grid(xDim, yDim);

            // Set the random number seed. Behaviour depends no whether this is a single run or part of a series of nReplicate runs. By default will assign every replicate the value ```seed=replicateId```
            if (seed != -1) {
                if (nReplicates == 1) {
                    myModel.SetSeed(seed);
                } else {
                    myModel.SetSeed(replicateId);
                }
            } else {
                myModel.SetSeed(replicateId);
            }

            // Set the logging behaviour
            // myModel.SetExtraSimulationInfo(new String[]{"ReplicateId", "InitSize", "RFrac"},
            //         new double[]{replicateId, initialSizeProp, rFrac});
            outFName = outDir + "RepId_" + replicateId + ".csv";
            if (imageFrequency > 0) {
                myModel.visualiseB = true;
                String currImgOutDir = imageOutDir + "RepId_" + replicateId;
                new File(currImgOutDir).mkdirs();
                myModel.ConfigureImaging(currImgOutDir, imageFrequency);
            }

            // Initialise the simulation
            myModel.InitialiseCellLog(outFName);
            // myModel.SetInitialState(initPopSizeArr, initialConditionType);
            // myModel.InitTumor(initRadius, rFrac);


        // Run the simulation
        // myModel.SetTreatmentSchedule(treatmentScheduleList);
        myModel.Run();
        myModel.Close();
        // if (saveModelState) {myModel.SaveModelState(currSavedModelFileName);}
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Model()).execute(args); 
        System.exit(exitCode);
    }
}
