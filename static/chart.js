const xmlhttp = new XMLHttpRequest();

xmlhttp.onreadystatechange = function() {
    if (this.readyState == 4 && this.status == 200) {
        const data = JSON.parse(this.responseText);
        const chart = document.getElementById("chart")
        const ctx = chart.getContext('2d');
        
        new Chart(ctx, {
            type: 'line',
            data: data,
            options: {
                scales: {
                    yAxes: [{
                        ticks: {
                            beginAtZero:true
                        }
                    }]
                }
            }
        });
    }
};
xmlhttp.onload = function() {
    if(xmlhttp.status != 200) {
        alert(xmlhttp.responseText)
    }
}

xmlhttp.open("GET", "/chartdata?amount=" + window.amount, true);
xmlhttp.send();