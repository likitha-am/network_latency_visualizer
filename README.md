## Network Latency Visualizer (Live)

A Java Swing–based desktop application that monitors and visualizes network latency in real time using system-level ping commands. The application allows users to add multiple hosts (IP addresses or domain names) and displays live round-trip time (RTT) trends through dynamically updating line graphs.

The tool continuously sends ping requests at fixed intervals, captures latency values, and renders them on a scrolling chart. Packet timeouts or failures are shown as gaps in the graph, making it easy to identify network instability or performance drops.

## Key Features

Real-time latency monitoring using OS-native ping

Supports multiple hosts simultaneously

Live graphical visualization with auto-scaling latency axis

Add/remove hosts dynamically during runtime

Color-coded graphs for each host

Handles timeouts and unreachable hosts gracefully

Thread-safe scheduled background pinging

## Tech Stack

Java

Swing (GUI)

ScheduledExecutorService (Concurrency)

ProcessBuilder (System ping execution)

## Use Cases

Network performance analysis

Connectivity and latency monitoring

Learning real-time data visualization in Java

Understanding concurrency and GUI threading

A lightweight, live network monitoring tool designed for clarity, simplicity, and real-time insight.
