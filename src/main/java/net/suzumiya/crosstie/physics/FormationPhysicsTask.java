package net.suzumiya.crosstie.physics;

import jp.ngt.rtm.entity.train.util.Formation;

import java.util.concurrent.Callable;

public class FormationPhysicsTask implements Callable<Void> {
    
    private final Formation formation;

    public FormationPhysicsTask(Formation formation) {
        this.formation = formation;
    }

    @Override
    public Void call() {
        try {
            PhysicsEngine.isWorkerThread.set(true);
            formation.updateTrainMovement();
        } finally {
            PhysicsEngine.isWorkerThread.set(false);
        }
        return null;
    }
}
