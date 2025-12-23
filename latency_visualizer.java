import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import javax.swing.Timer;
public class LatencyVisualizer extends JFrame {
    private final ChartPanel chartPanel = new ChartPanel();
    private final DefaultListModel<HostMonitor> hostListModel = new DefaultListModel<>();
    private final JList<HostMonitor> hostJList = new JList<>(hostListModel);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public LatencyVisualizer(String[] initialHosts) {
        super("Network Latency Visualizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLayout(new BorderLayout());

        JPanel left = new JPanel(new BorderLayout());
        left.setPreferredSize(new Dimension(260, 0));

        JPanel control = new JPanel();
        control.setLayout(new BoxLayout(control, BoxLayout.Y_AXIS));
        JTextField hostField = new JTextField();
        JButton addBtn = new JButton("Add Host");
        JButton removeBtn = new JButton("Remove Selected");

        control.add(new JLabel("Add host (hostname or IP):"));
        control.add(hostField);
        control.add(addBtn);
        control.add(Box.createVerticalStrut(8));
        control.add(removeBtn);
        control.add(Box.createVerticalStrut(12));
        left.add(control, BorderLayout.NORTH);

        hostJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hostJList.setCellRenderer(new HostCellRenderer());
        left.add(new JScrollPane(hostJList), BorderLayout.CENTER);

        JTextArea info = new JTextArea();
        info.setEditable(false);
        info.setRows(4);
        info.setText("Notes:\n- Uses system ping\n- Timeouts show as gaps\n- Add hosts to start pinging");
        left.add(new JScrollPane(info), BorderLayout.SOUTH);

        add(left, BorderLayout.WEST);
        add(chartPanel, BorderLayout.CENTER);

        addBtn.addActionListener(e -> {
            String h = hostField.getText().trim();
            if (!h.isEmpty()) {
                addHost(h);
                hostField.setText("");
            }
        });

        hostField.addActionListener(e -> addBtn.doClick());

        removeBtn.addActionListener(e -> {
            HostMonitor sel = hostJList.getSelectedValue();
            if (sel != null) removeHost(sel);
        });

        for (String h : initialHosts) {
            if (!h.isBlank()) addHost(h.trim());
        }

        Timer t = new Timer(800, e -> chartPanel.repaint());
        t.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    private void addHost(String host) {
        for (int i = 0; i < hostListModel.size(); i++) {
            if (hostListModel.get(i).host.equalsIgnoreCase(host)) return;
        }
        HostMonitor m = new HostMonitor(host, scheduler, chartPanel);
        hostListModel.addElement(m);
        chartPanel.addMonitor(m);
        m.start();
    }

    private void removeHost(HostMonitor m) {
        m.stop();
        chartPanel.removeMonitor(m);
        hostListModel.removeElement(m);
    }

    private void shutdown() {
        for (int i = 0; i < hostListModel.size(); i++) {
            hostListModel.get(i).stop();
        }
        scheduler.shutdownNow();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LatencyVisualizer app = new LatencyVisualizer(args);
            app.setVisible(true);
        });
    }

    static class HostMonitor {
        final String host;
        private final ScheduledExecutorService scheduler;
        private final ChartPanel chart;
        private ScheduledFuture<?> future;
        private final Deque<Double> samples = new ArrayDeque<>();
        private final int maxSamples = 120;
        private final Object lock = new Object();
        private volatile double lastKnown = Double.NaN;

        HostMonitor(String host, ScheduledExecutorService scheduler, ChartPanel chart) {
            this.host = host;
            this.scheduler = scheduler;
            this.chart = chart;
        }

        void start() {
            future = scheduler.scheduleAtFixedRate(() -> {
                double rtt = pingHost(host, 2000);
                synchronized (lock) {
                    if (samples.size() >= maxSamples) samples.removeFirst();
                    samples.addLast(rtt);
                    if (rtt >= 0) lastKnown = rtt;
                }
                SwingUtilities.invokeLater(chart::repaint);
            }, 0, 1, TimeUnit.SECONDS);
        }

        void stop() {
            if (future != null) future.cancel(true);
        }

        List<Double> getSamples() {
            synchronized (lock) {
                return List.copyOf(samples);
            }
        }

        double getLastKnown() { return lastKnown; }

        @Override
        public String toString() { return host; }
    }

    static class ChartPanel extends JPanel {
        private final List<HostMonitor> monitors = new CopyOnWriteArrayList<>();
        private final int maxSamples = 120;

        private final Color[] palette = {
                new Color(0x1f77b4), new Color(0xff7f0e),
                new Color(0x2ca02c), new Color(0xd62728),
                new Color(0x9467bd), new Color(0x8c564b),
                new Color(0xe377c2), new Color(0x7f7f7f)
        };

        ChartPanel() { setBackground(Color.WHITE); }

        void addMonitor(HostMonitor m) { monitors.add(m); }

        void removeMonitor(HostMonitor m) { monitors.remove(m); }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();

            int w = getWidth(), h = getHeight();
            int left = 60, right = 20, top = 20, bottom = 40;
            int plotW = w - left - right, plotH = h - top - bottom;

            if (plotW <= 0 || plotH <= 0) return;

            g.setColor(Color.LIGHT_GRAY);
            g.drawRect(left, top, plotW, plotH);

            double maxLatency = 100;
            for (HostMonitor m : monitors)
                for (double v : m.getSamples())
                    if (v > maxLatency) maxLatency = v;

            maxLatency = Math.ceil(maxLatency / 10) * 10;

            g.setFont(g.getFont().deriveFont(12f));
            g.setColor(Color.DARK_GRAY);

            for (int i = 0; i <= 5; i++) {
                int y = top + (int)(plotH * (i / 5.0));
                double value = maxLatency * (1 - i/5.0);

                g.drawLine(left - 4, y, left, y);
                g.drawString(String.format("%.0f ms", value), 6, y + 4);
            }

            int idx = 0;
            for (HostMonitor m : monitors) {
                List<Double> s = m.getSamples();
                int n = s.size();
                if (n < 2) { idx++; continue; }

                g.setColor(palette[idx % palette.length]);
                g.setStroke(new BasicStroke(2f));

                double xStep = (double) plotW / (maxSamples - 1);
                int prevX = -1, prevY = -1;

                int start = maxSamples - n;

                for (int i = 0; i < n; i++) {
                    double val = s.get(i);
                    if (val < 0) {
                        prevX = -1; prevY = -1;
                        continue;
                    }

                    int x = left + (int)((start + i) * xStep);
                    double frac = Math.min(1.0, val / maxLatency);
                    int y = top + (int)((1 - frac) * plotH);

                    if (prevX != -1) g.drawLine(prevX, prevY, x, y);

                    prevX = x; prevY = y;
                }

                double last = m.getLastKnown();
                g.fillRect(left + 10, top + 10 + idx * 18, 12, 8);
                g.setColor(Color.BLACK);
                g.drawString(
                        m.host + " (last: " + (Double.isNaN(last) ? "N/A" : String.format("%.1f ms", last)) + ")",
                        left + 28,
                        top + 18 + idx * 18
                );

                idx++;
            }

            g.dispose();
        }
    }

    static class HostCellRenderer extends JLabel implements ListCellRenderer<HostMonitor> {
        HostCellRenderer() { setOpaque(true); }

        @Override
        public Component getListCellRendererComponent(JList<? extends HostMonitor> list, HostMonitor value, int index, boolean isSelected, boolean cellHasFocus) {
            double last = value.getLastKnown();
            setText(value.host + (Double.isNaN(last) ? " (N/A)" : String.format(" (%.1f ms)", last)));
            setBackground(isSelected ? Color.LIGHT_GRAY : Color.WHITE);
            setForeground(Color.BLACK);
            return this;
        }
    }

    static double pingHost(String host, int timeoutMs) {
        String os = System.getProperty("os.name").toLowerCase();
        List<String> cmd = new ArrayList<>();

        if (os.contains("win")) {
            cmd.addAll(List.of("ping", "-n", "1", "-w", String.valueOf(timeoutMs), host));
        } else {
            int sec = Math.max(1, (timeoutMs + 999) / 1000);
            cmd.addAll(List.of("ping", "-c", "1", "-W", String.valueOf(sec), host));
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        try {
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line;
            StringBuilder out = new StringBuilder();
            while ((line = r.readLine()) != null) out.append(line).append("\n");

            p.waitFor(timeoutMs + 2000, TimeUnit.MILLISECONDS);

            String output = out.toString();

            Matcher m = Pattern.compile("time[=<]\\s*([0-9]+\\.?[0-9]*)").matcher(output);
            if (m.find()) return Double.parseDouble(m.group(1));

            m = Pattern.compile("time=\\s*([0-9]+\\.[0-9]+)\\s*ms").matcher(output);
            if (m.find()) return Double.parseDouble(m.group(1));

            return -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
