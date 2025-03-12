# Assignment1

## Description
This repository contains my solution for **Assignment 1**. The project demonstrates the use of multithreading for generating and sending POST requests in a load-testing scenario.

## Features
- Generates a set of events using a multithreaded approach.
- Sends HTTP POST requests to a specified server.
- Collects and analyzes response times, logging the data to a CSV file.
- Calculates performance metrics, including mean, median, throughput, and p99 latency.

## Installation

Clone this repository to your local machine:

   ```bash
   git clone https://github.com/Crystal0GMY/Assignment1.git
   cd Assignment1
   ```

## Configuration

To modify the URL path for the POST requests, you can update the following part in the MultithreadClient class: line 7, private static final String SERVER_URL

The main class to run is MultithreadClient

## Usage

Start the program to initiate the load testing process.
The program will generate events, send POST requests, and log the results to a CSV file.
After the test is completed, it will display key performance metrics.

## Metrics

The following metrics will be calculated and displayed:

Mean Response Time: The average time taken for requests.
Median Response Time: The middle value in the set of latencies.
Throughput: The total number of requests divided by the total time taken.
p99 Latency: The latency at the 99th percentile.
Min/Max Latency: The lowest and highest response times.
