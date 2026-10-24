package celtech.roboxbase.postprocessor.nouveau;

import celtech.roboxbase.postprocessor.nouveau.nodes.ExtrusionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.FillSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.GCodeEventNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.InnerPerimeterSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.LayerNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.SectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.SupportSectionNode;
import celtech.roboxbase.postprocessor.nouveau.nodes.providers.Movement;
import celtech.roboxbase.postprocessor.nouveau.nodes.providers.MovementProvider;
import java.util.Iterator;

/**
 * Speeds up internal extrusion moves that run well below the hot end's volumetric
 * flow capacity.
 *
 * For each eligible extrusion move the flow is (extruded volume / move length) * feedrate.
 * If that is below the configured maximum flow, the feedrate is raised so the flow
 * reaches the maximum (never beyond a speed cap, and never lowering a feedrate).
 *
 * Only inner perimeters, infill and support are touched; outer perimeters, skin
 * (top/bottom surfaces), skirts, support interfaces and the first layers keep the
 * speeds chosen by the slicer profile so visible surface quality is unaffected.
 */
public class FlowLimiter
{

    /** Layers up to and including this number are left untouched */
    private static final int LAST_UNTOUCHED_LAYER = 1;
    /** Very short moves can't reach a higher speed because of acceleration limits */
    private static final double MIN_SEGMENT_LENGTH_MM = 1.5;

    private final double maxFlow_mm3PerSec;
    private final double maxSpeed_mmPerSec;

    /**
     * @param maxFlow_mm3PerSec maximum volumetric flow; a value <= 0 disables the limiter
     * @param maxSpeed_mmPerSec absolute cap on the speed the limiter may set
     */
    public FlowLimiter(double maxFlow_mm3PerSec, double maxSpeed_mmPerSec)
    {
        this.maxFlow_mm3PerSec = maxFlow_mm3PerSec;
        this.maxSpeed_mmPerSec = maxSpeed_mmPerSec;
    }

    public boolean isEnabled()
    {
        return maxFlow_mm3PerSec > 0;
    }

    /**
     * Calculates the new speed for a move, or the current speed if it should not change.
     */
    static double calculateSpeed(double currentSpeed_mmPerSec, double volume_mm3,
            double length_mm, double maxFlow_mm3PerSec, double maxSpeed_mmPerSec)
    {
        if (currentSpeed_mmPerSec <= 0 || volume_mm3 <= 0 || length_mm < MIN_SEGMENT_LENGTH_MM)
        {
            return currentSpeed_mmPerSec;
        }
        double flowAtMaxSpeed = volume_mm3 / length_mm; // mm3 per mm
        double speedForMaxFlow = maxFlow_mm3PerSec / flowAtMaxSpeed;
        double newSpeed = Math.min(speedForMaxFlow, maxSpeed_mmPerSec);
        return Math.max(newSpeed, currentSpeed_mmPerSec);
    }

    public void process(LayerNode layerNode)
    {
        if (!isEnabled() || layerNode.getLayerNumber() <= LAST_UNTOUCHED_LAYER)
        {
            return;
        }

        boolean eligibleSection = false;
        boolean havePosition = false;
        double lastX = 0;
        double lastY = 0;

        Iterator<GCodeEventNode> iterator = layerNode.treeSpanningIterator(null);
        while (iterator.hasNext())
        {
            GCodeEventNode node = iterator.next();

            if (node instanceof SectionNode)
            {
                eligibleSection = node instanceof InnerPerimeterSectionNode
                        || node instanceof FillSectionNode
                        || node instanceof SupportSectionNode;
            }

            if (node instanceof MovementProvider)
            {
                Movement movement = ((MovementProvider) node).getMovement();
                double newX = movement.isXSet() ? movement.getX() : lastX;
                double newY = movement.isYSet() ? movement.getY() : lastY;

                if (node instanceof ExtrusionNode && eligibleSection && havePosition)
                {
                    ExtrusionNode extrusionNode = (ExtrusionNode) node;
                    double volume = Math.max(
                            extrusionNode.getExtrusion().isEInUse() ? extrusionNode.getExtrusion().getE() : 0,
                            extrusionNode.getExtrusion().isDInUse() ? extrusionNode.getExtrusion().getD() : 0);
                    double length = Math.hypot(newX - lastX, newY - lastY);
                    double currentSpeed = extrusionNode.getFeedrate().getFeedRate_mmPerSec();
                    double newSpeed = calculateSpeed(currentSpeed, volume, length,
                            maxFlow_mm3PerSec, maxSpeed_mmPerSec);
                    if (newSpeed > currentSpeed)
                    {
                        extrusionNode.getFeedrate().setFeedRate_mmPerSec(newSpeed);
                    }
                }

                lastX = newX;
                lastY = newY;
                havePosition = havePosition || movement.isXSet() || movement.isYSet();
            }
        }
    }
}
