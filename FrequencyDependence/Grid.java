package FrequencyDependence;

import HAL.Rand;
import HAL.Util;
import HAL.GridsAndAgents.AgentGrid2D;
import HAL.GridsAndAgents.AgentSQ2Dunstackable;
import HAL.Gui.GridWindow;
import static HAL.Util.*;

class Cell extends AgentSQ2Dunstackable<Grid> {
    public int type;

    // public float GetNeighborhood() {
    //     // get the proportion of cells of the same type in the neighborhood
    //     int hood = G.MapOccupiedHood(G.divHood, Xsq(), Ysq());
    //     int count = 0;
    //     for (int i = 0; i < hood; i++) {
    //         Cell other = G.GetAgent(G.divHood[i]);
    //         if (other != null && other.type == type) {
    //             count++;
    //         }
    //     }
    //     return (float) count / hood;
    // }

    public float GetNeighborhood() {
        // get the proportion of cells of the same type in the neighborhood
        if (G.localFreq == false){
            // calculate the global frequency
            if (this.type == Grid.RESISTANT){
                return (float) G.countResistant / G.Pop();
            } else {
                return (float) (G.Pop() - G.countResistant) / G.Pop();
            }
        } else {
            int hood = G.MapOccupiedHood(G.divHood, Xsq(), Ysq());
            int count = 0;
            for (int i = 0; i < hood; i++) {
                Cell other = G.GetAgent(G.divHood[i]);
                if (other != null && other.type == type) {
                    count++;
                }
            }
            return (float) count / hood;
        }
    }


    public double FreqDepFitness(){
        // returns the fitness as a function of cell type and local frequency
        
        if (type == Grid.RESISTANT) {
            float freq = GetNeighborhood();
            return (G.slope)*(1-freq) + G.resDivProb;
        } else {
            return G.wtDivProb;
        }
    }

    public void StepCell(double dieProb){
        if(G.rng.Double()<dieProb){
            Dispose();
            if (this.type == Grid.RESISTANT){
                G.countResistant--;
            }
            return;
        }
        double divProb=FreqDepFitness();
        if(G.rng.Double()<divProb){
            int options=MapEmptyHood(G.divHood);
            if(options>0){
                int iDaughter=G.divHood[G.rng.Int(options)];
                G.NewAgentSQ(iDaughter).type = this.type;
                if (this.type == Grid.RESISTANT){
                    G.countResistant++;
                }
                // daughter.StepCell(dieProb);
            }
        }
    }

}

public class Grid extends AgentGrid2D<Cell> {

    int[] divHood = Util.VonNeumannHood(false);
    public final static int RESISTANT = RGB(1, 1, 0), SENSITIVE = RGB(0, 0, 1);
    public boolean localFreq = false;
    public int countResistant;
    public double wtDivProb = 0.2;
    public double resDivProb = 0.15;
    public double gain = 1;
    public double slope = (wtDivProb - resDivProb)*gain;
    public double initRadius = 10;
    public double initResistantProp = 0.1;
    public int nReplicates = 1;
    public String imageOutDir = "data/images/";

    Rand rng = new Rand();
    int seed = 0;

    public Grid(int x, int y) {
        super(x, y, Cell.class);
    }

    public Grid(){
        super(100,100,Cell.class);
    }

    public void SetSeed(int seed) {
        this.rng = new Rand(seed);
        // this.rn_ICs = new Rand(seed);
    }

    // public Grid(int x, int y, double[] paramArr) {
    //     super(x, y, Cell.class);
    //     SetParameters(paramArr);
    // }

    // public void SetParameters(double[] paramArr) {
    //     this.divisionRate_S = paramArr[0];
    //     this.divisionRate_R = paramArr[1];
    //     this.movementRate_S = paramArr[2];
    //     this.movementRate_R = paramArr[3];
    //     this.deathRate_S = paramArr[4];
    //     this.deathRate_R = paramArr[5];
    //     this.drugEffect_div_S = paramArr[6];
    //     this.drugEffect_div_R = paramArr[7];
    // }

    public void StepCells(double dieProb) {
        for (Cell cell : this) {
            cell.StepCell(dieProb);
        }
    }

    public void DrawModel(GridWindow win) {
        for (int i = 0; i < length; i++) {
            // int color = Util.BLACK;
            Cell curAgent = GetAgent(i);
            if (curAgent != null) {
                // int color = curAgent.type;
                win.SetPix(i, curAgent.type);
            } else {
                win.SetPix(i, Util.BLACK);
            }
            
        }
    }
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
        double dieProb=0.01;
    
        GridWindow win=new GridWindow(x,y,10);
        Grid model=new Grid(x,y);

        // initialize model
        // model.NewAgentSQ(model.xDim/2,model.yDim/2);
        model.InitTumor(10, 0.1);

        // get the number of resistant cells in the tumor
        model.countResistant = 0;
        for (Cell cell : model) {
            if (cell.type == RESISTANT) {
                model.countResistant++;
            }
        }
    
        for (int i = 0; i < timesteps; i++) {
            // model step
            model.StepCells(dieProb);
            // draw
            model.DrawModel(win);
            win.TickPause(10);
            
        }
    }
    public void InitTumor(double radius, double resistantProb) {
        //get a list of indices that fill a circle at the center of the grid
        int[] tumorNeighborhood = CircleHood(true, radius);
        int hoodSize = MapHood(tumorNeighborhood, xDim / 2, yDim / 2);
        for (int i = 0; i < hoodSize; i++) {
            if (rng.Double() < resistantProb) {
                NewAgentSQ(tumorNeighborhood[i]).type = RESISTANT;
            } else {
                NewAgentSQ(tumorNeighborhood[i]).type = SENSITIVE;
            }
        }
    }
}
