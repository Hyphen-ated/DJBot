package hyphenated.djbot;

import hyphenated.djbot.robcamick.MessageConsole;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;


public class GuiWindow {
    public static void createAndShowGUI(int maxConsoleLines) {
        //Create and set up the window.
        JFrame frame = new JFrame("DJBot Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextComponent textComponent = new JTextArea(40, 160);
        frame.add( new JScrollPane( textComponent ) );
        MessageConsole mc = new MessageConsole(textComponent);
        mc.redirectOut();
        mc.redirectErr(Color.RED, null);
        mc.setMessageLines(maxConsoleLines);

        //Display the window.
        frame.pack();
        frame.setVisible(true);

    }

}
