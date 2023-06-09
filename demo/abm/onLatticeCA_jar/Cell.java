// ========================================================================
// Definition of a single cell.
// ========================================================================

package onLatticeCA_jar;

import HAL.GridsAndAgents.AgentSQ2Dunstackable;
import HAL.Util;

// ========================================================================
// Class to model individual cells
class Cell extends AgentSQ2Dunstackable<OnLatticeGrid> {
    double divisionRate;
    double movementRate;
    double deathRate;
    double drugEffect_div;
    int resistance; // 0 = sensitive, 1 = resistant

    // -----------------------------------
    boolean HasSpace(){
        return MapEmptyHood(G.hood)>0;
    }
    // -----------------------------------
    void Divide(){
        int nOpts = MapEmptyHood(G.hood); // Finds von neumann neighborhood indices around cell.
        int iDaughter = G.hood[G.rn.Int(nOpts)]; // Choose an option at random
        Cell daughter = G.NewAgentSQ(iDaughter); // Generate a daughter, the other is technically the original cell
        // Inherit division, movement and resistance characteristics (assuming no mutation here).
        daughter.divisionRate = divisionRate;
        daughter.movementRate = movementRate;
        daughter.deathRate = deathRate;
        daughter.drugEffect_div = drugEffect_div;
        daughter.resistance=resistance;
        daughter.Draw();
    }

    // -----------------------------------
    boolean Move(){
        boolean successfulMoveB = false;
        int nOpts=MapEmptyHood(G.hood);// identify the empty spots in the cell's neighbourhood (if there are any).
        if(nOpts>0){
            int iDestination = G.hood[G.rn.Int(nOpts)];
            MoveSafeSQ(G.ItoX(iDestination), G.ItoY(iDestination));
            successfulMoveB = true;
        }
        return successfulMoveB;
    }

    // -----------------------------------
    // Draws sensitive and resistant cells in different colours.
    // For green/red combo: G.vis.SetPix(Isq(), (resistance==0)? Util.RGB256(117, 197, 114): Util.RGB256(216, 27, 96));
    // Util.RGB256(79, 106, 184)
    void Draw(){
        G.vis.SetPix(Isq(), (resistance==0)? Util.RGB256(91, 123, 214): Util.RGB256(228, 234, 76));
    }
}