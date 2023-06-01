package Examples.FrequencyDependence;

import HAL.Rand;
import HAL.Util;
import HAL.GridsAndAgents.AgentGrid2D;
import HAL.GridsAndAgents.AgentSQ2Dunstackable;
import HAL.Gui.GridWindow;

class Cell extends AgentSQ2Dunstackable<Grid> {
    
    public void StepCell(double dieProb, double divProb){
        if(G.rng.Double()<dieProb){
            Dispose();
            return;
        }
        if(G.rng.Double()<divProb){
            int options=MapEmptyHood(G.divHood);
            if(options>0){
                int iDaughter=G.divHood[G.rng.Int(options)];
                Cell daughter=G.NewAgentSQ(iDaughter);
                daughter.StepCell(dieProb,divProb);
            }
        }
    }

}

public class Grid extends AgentGrid2D<Cell> {

    int[] divHood = Util.VonNeumannHood(false);

    Rand rng = new Rand();

    public Grid(int x, int y) {
        super(x, y, Cell.class);
    }

    public void StepCells(double dieProb, double divProb) {
        for (Cell cell : this) {
            cell.StepCell(dieProb, divProb);
        }
    }

    public void DrawModel(GridWindow win) {
        for (int i = 0; i < length; i++) {
            int color = Util.BLACK;
            if (GetAgent(i) != null) {
                color = Util.WHITE;
            }
            win.SetPix(i, color);
        }
    }
    public static void main(String[] args) {
        int x=100;
        int y=100;
        int timesteps=1000;
        double dieProb=0.1;
        double divProb=0.2;
    
        GridWindow win=new GridWindow(x,y,10);
        Grid model=new Grid(x,y);

        // initialize model
        model.NewAgentSQ(model.xDim/2,model.yDim/2);
    
        for (int i = 0; i < timesteps; i++) {
            // model step
            model.StepCells(dieProb,divProb);
            // draw
            model.DrawModel(win);
            win.TickPause(10);
        }
    }
}
