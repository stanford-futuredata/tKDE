tols = [0.9, 0.5, 0.2, 0.1, 0.05, 0.01, 0.001, 1.0e-5]
for i, tol in enumerate(tols):
    output = """
inputPath: "bigdata/otmy3.csv"
inputColumnRange: 0-3
inputRows: 0
timeToScore: 60.0

tKDEConf:
  algorithm: RKDE
  percentile: 0.01

  kernel: gaussian
  denormalized: true
  useStdDev: true
  ignoreSelfScoring: true

  calculateCutoffs: false
  tolAbsolute: {tol}

  leafSize: 20
  splitByWidth: true

  useGrid: false
""".format(
        tol=tol,
    )
    with open("./tmy3_t{i}.yaml".format(i=i), 'w') as f:
        f.write(output)