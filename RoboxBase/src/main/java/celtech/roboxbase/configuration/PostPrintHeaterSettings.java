package celtech.roboxbase.configuration;

/**
 * Global switch deciding whether the head and bed heaters are left on after a print
 * has finished or has been cancelled. When enabled the printer is not cooled down
 * automatically, so a failed print can be restarted without waiting for the printer
 * to cool and re-heat. The heaters can then be switched off manually.
 */
public class PostPrintHeaterSettings
{
    private static volatile boolean keepHeatersOnAfterPrint = false;

    public static boolean isKeepHeatersOnAfterPrint()
    {
        return keepHeatersOnAfterPrint;
    }

    public static void setKeepHeatersOnAfterPrint(boolean keepOn)
    {
        keepHeatersOnAfterPrint = keepOn;
    }
}
