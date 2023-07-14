package FrequencyDependence;

import HAL.Rand;
import HAL.Util;
import HAL.GridsAndAgents.AgentGrid2D;
import HAL.GridsAndAgents.AgentSQ2Dunstackable;
import HAL.GridsAndAgents.PDEGrid2D;
import HAL.Gui.GridWindow;
import static HAL.Util.*;
import HAL.Tools.FileIO;
import HAL.Gui.UIGrid;
import HAL.Interfaces.SerializableModel;

// class SrcOrSink extends AgentSQ2Dunstackable<DoseResponseGrid>{
//     int type;
//     void Init(int type){
//         this.type=type;
//     }
//     void Reaction(){
//         G.diff.Set(Isq(),type==DoseResponseGrid.SRC?1:0);//set the local concentration to 1 if source, 0 if sink
//     }
// }

class Source extends AgentSQ2Dunstackable<DoseResponseGrid>{
    double conc;
    void Init(int conc){
        this.conc=conc;
    }
    void Reaction(){
        G.diff.Set(Isq(),conc);
    }
}

class DiffusionGrid extends PDEGrid2D<Source>{
    public static int SRC=RGB(0,1,0);
    PDEGrid2D diff;

    public DiffusionGrid(int x, int y) {
        super(x, y, true, true);
    }

}

class Cell extends AgentSQ2Dunstackable<Grid> {
    int resistance; // 0 = sensitive, 1 = resistant
    double alpha;
    double beta;
    double divProb;

    void Draw(){
        G.vis.SetPix(Isq(), (resistance==0)? Util.RGB256(91, 123, 214): Util.RGB256(228, 234, 76));
    }

    public float GetNeighborhood() {
        // get the proportion of cells of the same type in the neighborhood
        if (G.localFreq == false){
            // calculate the global frequency
            if (this.resistance == 1){
                return (float) G.countResistant / G.Pop();
            } else {
                return (float) (G.Pop() - G.countResistant) / G.Pop();
            }
        } else { // calculate the local frequency
            int hood = G.MapOccupiedHood(G.freqHood, Xsq(), Ysq());
            int count = 0;
            for (int i = 0; i < hood; i++) {
                Cell other = G.GetAgent(G.freqHood[i]);
                if (other != null && other.resistance == this.resistance) {
                    count++;
                }
            }
            return (float) count / hood;
        }
    }
    
    public double fdsModel(double frac, double g_100) {
        double g_0 = (g_100 * (1 + alpha) + beta); // Maximally moderated growth (0% frac)
        double g_frac = frac * g_100 + (1 - frac) * g_0; // Growth rate (fraction)
        return g_frac;
    }

    public double fdsModel(double frac, double g_100, double alpha_t, double beta_t){
        double g_0 = (g_100 * (1 + alpha_t) + beta_t); // Maximally moderated growth (0% frac)
        double g_frac = frac * g_100 + (1 - frac) * g_0; // Growth rate (fraction)
        return g_frac;
    }

    public double FreqDepFitness(){
        // returns the fitness as a function of cell type and local frequency
        float freq = GetNeighborhood();
        return fdsModel(freq, divProb);        
    }

}

public class DoseResponseGrid extends AgentGrid2D<Cell> implements SerializableModel{
    
    int[] divHood = Util.VonNeumannHood(false);
    int[] freqHood = Util.VonNeumannHood(true); // include the current cell when calculating the local frequency 
    public final static int RESISTANT = RGB(1, 1, 0), SENSITIVE = RGB(0, 0, 1);
    public boolean localFreq = false;
    public int countResistant;
    public double wtDivProb = 0.033; // per hour
    public double resDivProb = 0.028;
    // public double gain = 1;
    // public double slope = (wtDivProb - resDivProb)*gain;
    public double initRadius = 10;
    public double initResistantProp = 0.1;
    public int nReplicates = 1;
    int nTSteps = 1000;
    double dieProb = 0.01;
    double dt = 1; // time step in hours

    double[] resGameParams = new double[]{0.1727,-0.00083}; // alpha, beta for BRAF/parental game
    double[] wtGameParams = new double[]{-0.0783,0.0020}; // alpha, beta for BRAF/parental game

    boolean visualiseB = true;
    public String imageOutDir = "data/images/";
    int imageFrequency = -1;
    int pause = 1;

    FileIO cellCountLogFile = null;
    String cellCountLogFileName = "data/cellCountLog/";
    double logCellCountFrequency = 1;
    int tIdx = 0;
    boolean saveModelState = false;

    Rand rng = new Rand();
    int seed = 0;

    // Output - visualization
    UIGrid vis;
    int scaleFactor = 2;

    public DoseResponseGrid(int x, int y) {
        super(x, y, Cell.class);
    }

    public DoseResponseGrid(){
        super(100,100,Cell.class);
    }

    public DoseResponseGrid(int x, int y , double[] paramArr, double dt){
        super(x,y,Cell.class);
        SetParameters(paramArr);
        this.dt = dt;
    }

    // Function used as part of SerializableModel to allow saving the model's state so that I can restart
    // the simulation exactly where I left off at the end of the last treatment interval.
    @Override
    public void SetupConstructors() {
        _PassAgentConstructor(Cell.class);
    }

    public void SetSeed(int seed) {
        this.rng = new Rand(seed);
        // this.rn_ICs = new Rand(seed);
    }
    public void ConfigureVisualisation(boolean visualiseB, int pause) {
        this.visualiseB = visualiseB;
        this.pause = pause;
    }

    public void ConfigureImaging(String imageOutDir, int imageFrequency) {
        /*
        * Configure location of and frequency at which tumour is imaged.
        */
        this.imageOutDir = imageOutDir;
        this.imageFrequency = imageFrequency;
    }

    public void ConfigureInitialTumour(double initRadius, double initResistantProp) {
        /*
        * Configure initial tumour size and composition.
        */
        this.initRadius = initRadius;
        this.initResistantProp = initResistantProp;
    }

    public void ConfigureGameParams(double[] wtGameParams, double[] resGameParams) {
        /*
        * Configure game parameters.
        */
        this.resGameParams = resGameParams;
        this.wtGameParams = wtGameParams;
    }

    public void StepCells() {
        int currPos;

        for (Cell cell : this) {
            
            // cell.StepCell(dieProb);
            if (rng.Double() < dieProb * dt){
                cell.Dispose();
                currPos = cell.Isq();
                vis.SetPix(currPos, BLACK);
                if (cell.resistance == 1){
                    countResistant--;
                }    
            }
            else{
                double divProb = cell.FreqDepFitness() * dt;
                
                if (rng.Double() < divProb) {
                    int options = MapEmptyHood(divHood, cell.Xsq(), cell.Ysq());
                    if (options > 0) {

                        int iDaughter = divHood[rng.Int(options)];
                        Cell daughter = NewAgentSQ(iDaughter);
                        
                        daughter.resistance = cell.resistance;
                        
                        if (daughter.resistance == 1){
                            daughter.alpha = resGameParams[0];
                            daughter.beta = resGameParams[1];
                            daughter.divProb = resDivProb;
                        }
                        else{
                            daughter.alpha = wtGameParams[0];
                            daughter.beta = wtGameParams[1];
                            daughter.divProb = wtDivProb;
                        }
                        daughter.Draw();
                        if (daughter.resistance == 1){
                            countResistant++;
                        }
                    }
                }
            }
        }
    }

    // public void DrawModel(GridWindow win) {
    //     for (int i = 0; i < length; i++) {
    //         // int color = Util.BLACK;
    //         Cell curAgent = GetAgent(i);
    //         if (curAgent != null) {
    //             // int color = curAgent.type;
    //             win.SetPix(i, curAgent.);
    //         } else {
    //             win.SetPix(i, Util.BLACK);
    //         }
            
    //     }
    // }
    // public void DrawModel(GridWindow vis) {
    //     for (int x = 0; x < xDim; x++) {
    //         for (int y = 0; y < yDim; y++) {
    //             Cell drawMe = GetAgent(x, y);

    //             vis.SetPix(i,drawMe.type);
       
    //             }
    //         }
    //     }
    
    public static void main(String[] args) {
        int x=100;
        int y=100;
        int timesteps=500;
        // double dieProb=0.01;
    
        GridWindow win = new GridWindow(x,y,10);
        Grid model = new Grid(x,y);

        // initialize model
        // model.NewAgentSQ(model.xDim/2,model.yDim/2);
        model.InitTumor(10, 0.1);

        // get the number of resistant cells in the tumor
        model.countResistant = 0;
        for (Cell cell : model) {
            if (cell.resistance == 1) {
                model.countResistant++;
            }
        }
    
        for (int i = 0; i < timesteps; i++) {
            // model step
            model.StepCells();
            // draw
            // model.DrawModel(win);
            win.TickPause(10);
            
        }
    }
    // public void InitTumor(double radius, double resistantProb) {
    //     //get a list of indices that fill a circle at the center of the grid
    //     int[] tumorNeighborhood = CircleHood(true, radius);
    //     int hoodSize = MapHood(tumorNeighborhood, xDim / 2, yDim / 2);
    //     for (int i = 0; i < hoodSize; i++) {
    //         if (rng.Double() < resistantProb) {
    //             NewAgentSQ(tumorNeighborhood[i]).resistance = 1;
    //             // assign alpha and beta

    //             countResistant++;
    //         } else {
    //             NewAgentSQ(tumorNeighborhood[i]).resistance = 0;
    //         }
    //     }
    // }

    public void InitTumor(double radius, double resistantProb) {
        //get a list of indices that fill a circle at the center of the grid
        int[] tumorNeighborhood = CircleHood(true, radius);
        int hoodSize = MapHood(tumorNeighborhood, xDim / 2, yDim / 2);
        for (int i = 0; i < hoodSize; i++) {
            Cell c = NewAgentSQ(tumorNeighborhood[i]);
            if (rng.Double() < resistantProb) {
                c.resistance = 1;
                c.divProb = resDivProb;
                // assign alpha and beta
                c.alpha = resGameParams[0];
                c.beta = resGameParams[1];
                countResistant++;
            } else {
                c.resistance = 0;
                c.divProb = wtDivProb;
                // assign alpha and beta
                c.alpha = wtGameParams[0];
                c.beta = wtGameParams[1];
            }
        }
    }

    public void Run() {
    // Initialise visualisation window
        UIGrid currVis = new UIGrid(xDim, yDim, scaleFactor, visualiseB); // For head-less run
        this.vis = currVis;
        // Comment out next line if want to run with viz on in vscode xxx
        // GridWindow vis=new GridWindow(xDim,yDim,scaleFactor); vis.AddAlphaGrid(currVis);
        Boolean completedSimulationB = false;
        Boolean logged = false;
        // currDrugConcentration = treatmentScheduleList[0][2];
        // Set up the grid and initialise log if this is the beginning of the simulation
        if (tIdx==0) {
            // if (initialConditionType.equalsIgnoreCase("random")) {
            //     InitSimulation_Random(initPopSizeArr[0], initPopSizeArr[1]);
            // } else if (initialConditionType.equalsIgnoreCase("circle")) {
            //     InitSimulation_Circle(initPopSizeArr[0], initPopSizeArr[1]);
            // }            
            // PrintStatus(0);
            InitTumor(initRadius, initResistantProp);
            if (cellCountLogFile==null && cellCountLogFileName!=null) {InitialiseCellLog(this.cellCountLogFileName);}

            for (Cell c : this) {
                c.Draw();
            }
            SaveCurrentCellCount(0);
            SaveTumourImage(tIdx);
            tIdx = 1;
        } else {
            // Continue from a restart
            if (cellCountLogFileName!=null) {
                cellCountLogFile = new FileIO(cellCountLogFileName, "a");
            }
            for (Cell c : this) {
                c.Draw();
            }
        }

        // Run the simulation
        // double currIntervalEnd;
        // if (treatmentScheduleList==null) treatmentScheduleList = new double[][]{{0,tEnd,currDrugConcentration}};
        // for (int intervalIdx=0; intervalIdx<treatmentScheduleList.length; intervalIdx++) {
        //     currIntervalEnd = treatmentScheduleList[intervalIdx][1];
        //     nTSteps = (int) Math.ceil(currIntervalEnd/dt);
        //     currDrugConcentration = treatmentScheduleList[intervalIdx][2];
        completedSimulationB = false;
        while (!completedSimulationB) {
            vis.TickPause(pause);
            StepCells();
            // PrintStatus(tIdx);
            logged = SaveCurrentCellCount(tIdx);
            SaveTumourImage(tIdx);
            tIdx++;
            // Check if the stopping condition is met
            completedSimulationB = (tIdx>nTSteps)?true:false;
        }
        // }

        // Close the simulation
        this.Close(logged);
    }
    // ------------------------------------------------------------------------------------------------------------
    // Manage and save output
    // ------------------------------------------------------------------------------------------------------------

    public void InitialiseCellLog(String cellCountLogFileName) {
        cellCountLogFile = new FileIO(cellCountLogFileName, "w");
        WriteLogFileHeader();
        this.cellCountLogFileName = cellCountLogFileName;
        this.logCellCountFrequency = 1;
    }

    public Boolean SaveCurrentCellCount(int currTimeIdx) {
        Boolean successfulLog = false;
        if ((currTimeIdx % (int) (logCellCountFrequency)) == 0 && logCellCountFrequency > 0) {
            cellCountLogFile.WriteDelimit(GetModelState(),",");
            // if (extraSimulationInfoNames!=null) {
            //     cellCountLogFile.Write(",");
            //     cellCountLogFile.WriteDelimit(extraSimulationInfo, ",");
            // }
            cellCountLogFile.Write("\n");
            successfulLog = true;
        }
        return successfulLog;
    }

    public double[] GetModelState() {
        // return new double[] {tIdx, tIdx, cellCountsArr[0], cellCountsArr[1], Util.ArraySum(cellCountsArr),
        //         currDrugConcentration, divisionRate_S, divisionRate_R, movementRate_S, movementRate_R,
        //         deathRate_S, deathRate_R, drugEffect_div_S, drugEffect_div_R, dt};
        if (localFreq == false) {
            // compute global frequency
            
            double resFreq = (double) countResistant / (double) Pop();
            int agentIndx = 0;
            
            // get an example cell

            Cell c = (Cell) this.GetAgent(agentIndx);

            while (c == null) {
                agentIndx++;
                c = this.GetAgent(agentIndx);
            }
            // compute resistant fitness
            double resFitness = c.fdsModel(resFreq, resDivProb, resGameParams[0], resGameParams[1]);
            // compute wildtype fitness
            double wtFitness = c.fdsModel(1-resFreq, wtDivProb, wtGameParams[0], wtGameParams[1]);

            return new double[] {tIdx, tIdx*dt, Pop()-countResistant, countResistant, Pop(),
            resFreq, resFitness, wtFitness};
        }
        else{
            return new double[] {tIdx, tIdx*dt, Pop()-countResistant, countResistant, Pop()};
        }
    }

    public void SaveTumourImage(int currTimeIdx) {
        if (imageFrequency > 0 && (currTimeIdx % (int) (imageFrequency/dt)) == 0) {
            this.vis.ToPNG(imageOutDir +"/img_t_"+currTimeIdx*dt+".png");
        }
    }
    // public void InitialiseCellLog(String cellCountLogFileName) {
    //     InitialiseCellLog(cellCountLogFileName, 1.);
    // }

    // public void InitialiseCellLog(String cellCountLogFileName, double frequency) {
    //     InitialiseCellLog(cellCountLogFileName,frequency,false);
    // }

    // public void InitialiseCellLog(String cellCountLogFileName, double frequency, Boolean profilingMode) {
    //     cellCountLogFile = new FileIO(cellCountLogFileName, "w");
    //     WriteLogFileHeader();
    //     this.cellCountLogFileName = cellCountLogFileName;
    //     this.logCellCountFrequency = frequency;
    //     if (profilingMode) {
    //         double[] tmpArr = GetModelState();
    //         int extraFields = extraSimulationInfoNames==null? 0: extraSimulationInfoNames.length;
    //         this.outputArr = new double[5][tmpArr.length+extraFields];
    //         // Initialise the logging array
    //         for (int i=0; i<outputArr.length; i++) {for (int j=0; j<outputArr[0].length; j++) {outputArr[i][j] = 0;}}
    //     }
    // }

    public void SetParameters(double[] paramArr){
        this.wtDivProb = paramArr[0];
        this.resDivProb = paramArr[1];
        // this.gain = paramArr[2];
        // this.slope = (wtDivProb - resDivProb)*gain;
        // this.initRadius = paramArr[3];
        // this.initResistantProp = paramArr[4];
        this.nTSteps = (int) paramArr[2];
        this.dieProb = paramArr[3];
        this.localFreq = (paramArr[4] == 1);
        // this.seed = (int) paramArr[8];
        // this.imageFrequency = (int) paramArr[9];
        // this.pause = (int) paramArr[10];
        // this.saveModelState = (paramArr[11] == 1);
    }

    public void SetInitialState(double initRadius, double initResistantProp) {
        this.initRadius = initRadius;
        this.initResistantProp = initResistantProp;
    }

    private void WriteLogFileHeader() {
        // cellCountLogFile.Write("TIdx,Time,NCells_S,NCells_R,NCells,DrugConcentration,rS,rR,mS,mR,dS,dR,dD_div_S,dD_div_R,dt");
        if (localFreq == false){
            cellCountLogFile.Write("TIdx,Time,NCells_S,NCells_R,NCells,ResFreq,ResFitness,WtFitness");
        }
        else{
            cellCountLogFile.Write("TIdx,Time,NCells_S,NCells_R,NCells");
        }
        // cellCountLogFile.Write("TIdx,Time,NCells_S,NCells_R,NCells");
        // if (extraSimulationInfoNames!=null) {
        //     cellCountLogFile.Write(",");
        //     cellCountLogFile.WriteDelimit(extraSimulationInfoNames, ",");
        // }
        cellCountLogFile.Write("\n");
    }
    public void Close() {
        if (cellCountLogFile!=null) {cellCountLogFile.Close();}
    }

    public void Close(Boolean logged) {
        if (!logged) {
            tIdx--;
            SaveCurrentCellCount(0);}
        if (cellCountLogFile!=null) {cellCountLogFile.Close();}
    }
    public void SaveModelState(String stateFileName) {
        // Can't have active pointers when saving the model. So, close everything here.
        if (cellCountLogFile!=null) {
            cellCountLogFile.Close();
            cellCountLogFile = null;
        }
        if (vis!=null) {vis = null;}
        SaveState(this,stateFileName);
    }
}
