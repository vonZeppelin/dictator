/**
 * Copyright 2014 Leonid Bogdanov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dictator;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

/**
 * The <code>LCDPanel</code> class represents the LCD panel Swing component.
 *
 * @author Leonid Bogdanov
 */
public final class LCDPanel extends JPanel {
    private static final String NO_TIME = "12:45";

    private final JLabel timer;

    public LCDPanel() {
        super(new MigLayout("wrap 2", "[grow 120]unrel[]", "[]unrel[]"));
        setBackground(Color.BLACK);

        Font clockFont;
        try (InputStream is = getClass().getResourceAsStream("/lcd.ttf")) {
            clockFont = Font.createFont(Font.TRUETYPE_FONT, is);
        } catch (IOException | FontFormatException e) {
            clockFont = Font.decode(null);
        }

        timer = new JLabel(NO_TIME, JLabel.CENTER);
        timer.setFont(clockFont.deriveFont(30f));
        timer.setForeground(Color.CYAN.brighter());
        add(timer, "growx,span 2");
    }

    public void startTimer() {}

    public void stopTimer() {}
}
