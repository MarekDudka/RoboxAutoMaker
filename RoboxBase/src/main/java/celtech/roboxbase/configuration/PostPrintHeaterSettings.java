package celtech.roboxbase.configuration;

/**
 * Global switch deciding whether the head and bed heaters are left on after a print
 * has finished or has been cancelled. When enabled the printer is not cooled down
 * automatically, so a failed print can be restarted without waiting for the printer
 * to cool and re-heat. The heaters can then be switched off manually.
 */
public class PostPrintHeaterSettings
{
    /** Ambient target that makes the printer's main fan run to cool the bed area */
    public static final int COOLING_AMBIENT_TARGET_C = 20;

    private static volatile boolean keepHeatersOnAfterPrint = false;

    private static volatile boolean keepFirstLayerBedTemperature = false;

    /**
     * When set, the bed stays at the first layer temperature for the whole print.
     * Otherwise the bed target is lowered after the first layer and the printer
     * actively cools the bed with its fan to reach it.
     */
    public static boolean isKeepFirstLayerBedTemperature()
    {
        return keepFirstLayerBedTemperature;
    }

    public static void setKeepFirstLayerBedTemperature(boolean keep)
    {
        keepFirstLayerBedTemperature = keep;
    }

    public static boolean isKeepHeatersOnAfterPrint()
    {
        return keepHeatersOnAfterPrint;
    }

    public static void setKeepHeatersOnAfterPrint(boolean keepOn)
    {
        keepHeatersOnAfterPrint = keepOn;
    }
}
