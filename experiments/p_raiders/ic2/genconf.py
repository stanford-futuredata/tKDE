ps = [1,5,10,20,30,40,50,60,70,80,90,95,99]
for p in ps:
    if p > 60:
        qs = 4000
    else:
        qs = 20000
    output = """
inputPath: "bigdata/otmy3.csv"
inputColumnRange: 0-3
inputRows: 0
timeToScore: 600.0

tKDEConf:
  algorithm: TREEKDE
  percentile: {p}

  qSampleSize: {qs}
  qCutoffMultiplier: 1.1
  qTolMultiplier: 0.1

  kernel: gaussian
  denormalized: false
  bwMultiplier: 1.0
  useStdDev: true
  ignoreSelfScoring: true

  calculateCutoffs: true

  leafSize: 20
  splitByWidth: true

  useGrid: true
  gridSizes:
    - 0.8
""".format(p=0.01 * p, qs=qs)
    with open("tmy3_{}.yaml".format(p), 'w') as f:
        f.write(output)