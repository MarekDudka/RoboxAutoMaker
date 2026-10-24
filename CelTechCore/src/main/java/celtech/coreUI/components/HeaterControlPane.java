package celtech.coreUI.components;

import celtech.Lookup;
import celtech.roboxbase.configuration.PostPrintHeaterSettings;
import celtech.roboxbase.printerControl.model.HeaterMode;
import celtech.roboxbase.printerControl.PrinterStatus;
import celtech.roboxbase.printerControl.model.Head;
import celtech.roboxbase.printerControl.model.NozzleHeater;
import celtech.roboxbase.printerControl.model.Printer;
import celtech.roboxbase.printerControl.model.PrinterException;
import java.util.Optional;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import libertysystems.stenographer.Stenographer;
import libertysystems.stenographer.StenographerFactory;

/**
 * Shows a warning while the printer is idle but its head or bed is still hot, and
 * offers buttons (with confirmation) to switch the head and bed heaters off.
 * When the "keep heaters on after print" preference is set, the printer's ambient LED
 * is also switched to red while the printer is hot.
 */
public class HeaterControlPane extends VBox
{

    private static final Stenographer steno = StenographerFactory.getStenographer(
            HeaterControlPane.class.getName());

    private static final int HOT_THRESHOLD_C = 50;
    private static final Color WARNING_LED_COLOUR = Color.RED;

    private final Label warningLabel = new Label();
    private final Button coolHeadButton = new Button();
    private final Button coolBedButton = new Button();
    private final CheckBox keepWarmCheckBox = new CheckBox();
    private final Timeline refreshTimeline;

    private Printer printer = null;
    private boolean ledWarningActive = false;
    // Heater targets seen while printing, used to restore them if the print's own
    // G-code switched the heaters off (it was generated before "keep warm" was enabled)
    private PrinterStatus lastStatus = null;
    private final int[] lastNozzleTargets = new int[2];
    private int lastBedTarget = 0;

    // Fans switched on by us to cool down: head fan for the head, ambient (bed area)
    // fan for the bed
    private boolean headFanActive = false;
    private boolean bedFanActive = false;
    /** Ambient target set to make the ambient fan run; ambient control is turned off afterwards */
    private static final int COOLING_AMBIENT_TARGET_C = 20;

    public HeaterControlPane()
    {
        setAlignment(Pos.CENTER);
        setSpacing(8);
        setPadding(new Insets(4, 0, 4, 0));
        setPrefWidth(260);
        setMaxWidth(260);

        warningLabel.setStyle("-fx-text-fill: #ff661b; -fx-font-size: 12px; -fx-font-weight: bold;");
        warningLabel.setWrapText(true);
        warningLabel.setMaxWidth(240);

        coolHeadButton.setText(text("heaterControl.coolHead", "Cool Head"));
        coolBedButton.setText(text("heaterControl.coolBed", "Cool Bed"));
        coolHeadButton.setOnAction(e -> confirmAndRun(
                text("heaterControl.confirmCoolHead", "Switch off the head heater?"), () -> startHeadCooling()));
        coolBedButton.setOnAction(e -> confirmAndRun(
                text("heaterControl.confirmCoolBed", "Switch off the bed heater?"), () -> startBedCooling()));

        // Equal width buttons that fill their group
        for (Button button : new Button[]
        {
            coolHeadButton, coolBedButton
        })
        {
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinWidth(0);
        }
        HBox.setHgrow(coolHeadButton, Priority.ALWAYS);
        HBox.setHgrow(coolBedButton, Priority.ALWAYS);
        HBox buttons = new HBox(8, coolHeadButton, coolBedButton);
        buttons.setAlignment(Pos.CENTER);

        keepWarmCheckBox.setText(text("heaterControl.keepWarm", "Keep warm after print"));
        keepWarmCheckBox.setStyle("-fx-text-fill: white;");
        keepWarmCheckBox.selectedProperty().bindBidirectional(
                Lookup.getUserPreferences().keepHeatersOnAfterPrintProperty());

        keepWarmCheckBox.setMaxWidth(Double.MAX_VALUE);

        VBox heaterGroup = createGroup(text("heaterControl.groupHeaters", "Heaters"),
                warningLabel, buttons, keepWarmCheckBox);
        getChildren().addAll(heaterGroup);

        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> refresh()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();

        setPrinter(null);
    }

    private static VBox createGroup(String title, javafx.scene.Node... content)
    {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #a8a8a8; -fx-font-size: 11px; -fx-font-weight: bold;");

        VBox group = new VBox(6);
        group.setAlignment(Pos.CENTER_LEFT);
        group.setPadding(new Insets(6, 8, 8, 8));
        group.setPrefWidth(260);
        group.setMaxWidth(260);
        group.setStyle("-fx-border-color: #5a5a5a; -fx-border-width: 1; -fx-border-radius: 3;");
        group.getChildren().add(titleLabel);
        group.getChildren().addAll(content);
        return group;
    }

    /**
     * Looks up a translated string, falling back to the English default if the
     * language files in use don't contain the key.
     */
    private static String text(String key, String defaultText)
    {
        try
        {
            String value = Lookup.i18n(key);
            if (value != null && !value.isEmpty() && !value.equals(key) && !value.contains("heaterControl."))
            {
                return value;
            }
        } catch (RuntimeException ex)
        {
            // fall through to default
        }
        return defaultText;
    }

    public final void setPrinter(Printer newPrinter)
    {
        if (ledWarningActive)
        {
            restoreLED();
        }
        printer = newPrinter;
        lastStatus = null;
        lastNozzleTargets[0] = 0;
        lastNozzleTargets[1] = 0;
        lastBedTarget = 0;
        headFanActive = false;
        bedFanActive = false;
        refresh();
    }

    private int getMaxNozzleTemperature()
    {
        Head head = printer.headProperty().get();
        int max = 0;
        if (head != null)
        {
            for (NozzleHeater heater : head.getNozzleHeaters())
            {
                max = Math.max(max, heater.nozzleTemperatureProperty().get());
            }
        }
        return max;
    }

    private void refresh()
    {
        if (printer == null)
        {
            setVisible(false);
            setManaged(false);
            return;
        }

        restoreHeatersIfPrintSwitchedThemOff();

        int nozzleTemp = getMaxNozzleTemperature();
        int bedTemp = printer.getPrinterAncillarySystems().bedTemperatureProperty().get();
        boolean idle = printer.printerStatusProperty().get() == PrinterStatus.IDLE;
        boolean nozzleHot = nozzleTemp >= HOT_THRESHOLD_C;
        boolean bedHot = bedTemp >= HOT_THRESHOLD_C;
        boolean showWarning = idle && (nozzleHot || bedHot);

        warningLabel.setText(text("heaterControl.hotWarning", "WARNING: printer is hot!") + " "
                + text("heaterControl.head", "Head") + " " + nozzleTemp + "°C, "
                + text("heaterControl.bed", "Bed") + " " + bedTemp + "°C");
        warningLabel.setVisible(showWarning);
        warningLabel.setManaged(showWarning);

        coolHeadButton.setDisable(!(idle && nozzleHot));
        coolBedButton.setDisable(!(idle && bedHot));

        setVisible(true);
        setManaged(true);

        updateLED(showWarning && PostPrintHeaterSettings.isKeepHeatersOnAfterPrint());
    }

    /**
     * The finish-of-print macro is part of the G-code that was generated when the
     * print was sliced. If "keep warm" was enabled after that, the macro still
     * switches the heaters off. When the print ends, put back the heaters that are
     * off but still hot.
     */
    private void restoreHeatersIfPrintSwitchedThemOff()
    {
        PrinterStatus status = printer.printerStatusProperty().get();
        Head head = printer.headProperty().get();

        if (status == PrinterStatus.PRINTING_PROJECT)
        {
            if (head != null)
            {
                for (int i = 0; i < Math.min(2, head.getNozzleHeaters().size()); i++)
                {
                    int target = head.getNozzleHeaters().get(i).nozzleTargetTemperatureProperty().get();
                    if (target > 0)
                    {
                        lastNozzleTargets[i] = target;
                    }
                }
            }
            int bedTarget = printer.getPrinterAncillarySystems().bedTargetTemperatureProperty().get();
            if (bedTarget > 0)
            {
                lastBedTarget = bedTarget;
            }
        } else if (lastStatus != null && lastStatus != PrinterStatus.IDLE
                && status == PrinterStatus.IDLE
                && PostPrintHeaterSettings.isKeepHeatersOnAfterPrint())
        {
            if (head != null)
            {
                for (int i = 0; i < Math.min(2, head.getNozzleHeaters().size()); i++)
                {
                    NozzleHeater heater = head.getNozzleHeaters().get(i);
                    if (lastNozzleTargets[i] > 0
                            && heater.heaterModeProperty().get() == HeaterMode.OFF
                            && heater.nozzleTemperatureProperty().get() >= HOT_THRESHOLD_C)
                    {
                        steno.info("Restoring nozzle " + i + " heater to " + lastNozzleTargets[i]);
                        printer.setNozzleHeaterTargetTemperature(i, lastNozzleTargets[i]);
                    }
                }
            }
            if (lastBedTarget > 0
                    && printer.getPrinterAncillarySystems().bedHeaterModeProperty().get() == HeaterMode.OFF
                    && printer.getPrinterAncillarySystems().bedTemperatureProperty().get() >= HOT_THRESHOLD_C)
            {
                steno.info("Restoring bed heater to " + lastBedTarget);
                printer.setBedTargetTemperature(lastBedTarget);
            }
        }
        else if (lastStatus != null && lastStatus != PrinterStatus.IDLE
                && status == PrinterStatus.IDLE
                && !PostPrintHeaterSettings.isKeepHeatersOnAfterPrint())
        {
            startCooldownIfHot(head);
        }

        manageCooldownFans(status, head);
        lastStatus = status;
    }

    /**
     * Keep warm is off, so the printer should cool down. If the print's G-code was
     * generated while keep warm was on it has left the heaters on and the fans off,
     * so cool the head and the bed.
     */
    private void startCooldownIfHot(Head head)
    {
        if (getMaxNozzleTemperature() >= HOT_THRESHOLD_C)
        {
            startHeadCooling();
        }
        if (printer.getPrinterAncillarySystems().bedTemperatureProperty().get() >= HOT_THRESHOLD_C)
        {
            startBedCooling();
        }
    }

    /** Head heater off and head fan on until the head is cool */
    private void startHeadCooling()
    {
        try
        {
            printer.switchAllNozzleHeatersOff();
            printer.switchOnHeadFan();
            headFanActive = true;
        } catch (PrinterException ex)
        {
            steno.warning("Couldn't start head cooling: " + ex.getMessage());
        }
    }

    /** Bed heater off and the ambient (bed area) fan on until the bed is cool */
    private void startBedCooling()
    {
        printer.switchBedHeaterOff();
        printer.setAmbientTemperature(COOLING_AMBIENT_TARGET_C);
        bedFanActive = true;
    }

    private void manageCooldownFans(PrinterStatus status, Head head)
    {
        if (!headFanActive && !bedFanActive)
        {
            return;
        }
        boolean idle = status == PrinterStatus.IDLE;

        if (headFanActive && (!idle || getMaxNozzleTemperature() < HOT_THRESHOLD_C))
        {
            headFanActive = false;
            if (idle)
            {
                try
                {
                    printer.switchOffHeadFan();
                } catch (PrinterException ex)
                {
                    steno.warning("Couldn't switch off head fan: " + ex.getMessage());
                }
            }
        }
        if (bedFanActive && (!idle
                || printer.getPrinterAncillarySystems().bedTemperatureProperty().get() < HOT_THRESHOLD_C))
        {
            bedFanActive = false;
            if (idle)
            {
                printer.setAmbientTemperature(0); // ambient control off
            }
        }
    }

    private void updateLED(boolean warn)
    {
        if (warn && !ledWarningActive)
        {
            try
            {
                printer.setAmbientLEDColour(WARNING_LED_COLOUR);
                ledWarningActive = true;
            } catch (PrinterException ex)
            {
                steno.warning("Couldn't set warning LED colour: " + ex.getMessage());
            }
        } else if (!warn && ledWarningActive)
        {
            restoreLED();
        }
    }

    private void restoreLED()
    {
        ledWarningActive = false;
        if (printer != null)
        {
            Color original = printer.getPrinterIdentity().printerColourProperty().get();
            if (original != null)
            {
                try
                {
                    printer.setAmbientLEDColour(original);
                } catch (PrinterException ex)
                {
                    steno.warning("Couldn't restore LED colour: " + ex.getMessage());
                }
            }
        }
    }

    private void confirmAndRun(String message, Runnable action)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(text("heaterControl.confirmTitle", "Confirm"));
        alert.setHeaderText(null);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES && printer != null)
        {
            action.run();
            refresh();
        }
    }
}
