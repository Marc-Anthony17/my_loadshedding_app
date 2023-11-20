 var province, stage, city, store;
 var count = 0;
  const options = {
    method: 'GET',
  };
  window.addEventListener("load", () => {
      fetch(`http://localhost:7000/provinces`, options)
          .then(response => response.json())
          .then(data => {
            data = {
              provinces: data
            };
            const template = document.getElementById('province-template').innerText;
            const compiledFunction = Handlebars.compile(template);
            document.getElementById('province').innerHTML = compiledFunction(data);
          });
  });

  async function loadCities(){
    this.province = await document.getElementById("provinces_select").value;

    fetch(`http://localhost:7000/towns/${province}`, options)
        .then(response => response.json())
        .then(data => {
          data = {
            cities: data
          };

          const template = document.getElementById('city-template').innerText;
          const compiledFunction = Handlebars.compile(template);
          document.getElementById('city').innerHTML = compiledFunction(data);
        });
  };

  async function loadSchedule() {
      this.city = await document.getElementById("city_select").value;
      await fetch(`http://localhost:7001/stage`, options)
                .then(response => response.json())
                .then(data => {
                  data = {
                    stage: data.stage
                  };
                  stage = data.stage;
                });

      fetch(`http://localhost:7002/${province}/${city}/${stage}`, options)
          .then(response => response.json())
          .then(data => {
            data = {
                days: data.days,
                startDate: getFinalDates(data.startDate),
                test : data
           };

            this.store = data.startDate;
              const template = document.getElementById('schedule-template').innerText;
              const compiledFunction = Handlebars.compile(template);
              document.getElementById('schedule').innerHTML = compiledFunction(data);
              });
        };

  Handlebars.registerHelper('if_equal', function(a, b, opts) {
     return a == b ? opts.fn(this) : opts.inverse(this);
  });

  Handlebars.registerHelper('displayList', function(list) {
    count = count == 4 ? 0 : count;
    result = `<p> ${store[count]} </p>`;
    count++;
    return new Handlebars.SafeString(result);
  });

Handlebars.registerHelper('displayStage', function() {
      var result = `<p> ${stage} </p> `;
      return new Handlebars.SafeString(result);
    });

function getFinalDates(list) {
  var arr = [];
  let clonedList = list.slice();
  for (let i = 0; i < 4; i++) {
    let str = "";
    clonedList[clonedList.length - 1] += i;
    clonedList.forEach(function (element) {
      str += element + " ";
    });
    arr.push(str);
  }
  return arr;
}




