$(document).ready(function () {
  $("#home").attr("href", "/");

  let images = [];

  get("https://uw47difk8j.execute-api.eu-central-1.amazonaws.com/images", function (data) {
    data.forEach((image) => images.push({ url: image }));
    loadMore(images);
  });

  function loadMore(images) {
    let template = $("#image-card-template").html();
    for (let i = 0; i < 6; i++) {
      let imageData = images.shift();
      if (imageData) {
        $(".row").append(Mustache.render(template, imageData));
      }
    }

    $(".image").unbind("click").on("click", bindExpandedImage);
    $(".info-button").unbind("click").on("click", showDescription);
    $(".play-button").unbind("click").on("click", openPlayer);

    hideAppearingElements();
    bindForMobileDevices();
  }

  // Clear description on click outside or "Info" button click
  $(document).on("mouseover", function (event) {
    if (!$(event.target).closest(".image-card").length) {
      hideAppearingElements();
    }
  });

  $("#upload-file").on("click", function (event) {
    $("#upload-file-input").trigger("click");
    event.preventDefault();
  });

  $("#upload-file-input").on("change", function () {
    const file = $(this).prop("files")[0];
    const url = "https://uw47difk8j.execute-api.eu-central-1.amazonaws.com/images/upload";

    const formData = new FormData();
    formData.append("file", file);
    const fetchOptions = {
      method: "POST",
      body: formData,
    };

    fetch(url, fetchOptions)
      .then((response) => response.json())
      .then((data) => {
        var fileName = { fileName: file.name };

        let alertTemplate = $("#upload-alert-template").html();
        $(".header").prepend(Mustache.render(alertTemplate, fileName));

        let template = $("#image-card-template").html();
        $(".row").prepend(Mustache.render(template, data));
      });
  });

  $("#load-more").on("click", function (event) {
    event.preventDefault();
    loadMore(images);
    checkIfLoadMoreRequired();
  });

  checkIfLoadMoreRequired();

  function checkIfLoadMoreRequired() {
    if (images.length > 0) {
      $("#load-more").show();
    } else {
      $("#load-more").hide();
    }
  }

  function showDescription() {
    const parent = $(this).closest(".image-card");
    const rekognitionData = new FormData();
    rekognitionData.append("imageKey", "<paste_image_key>");
    post('https://uw47difk8j.execute-api.eu-central-1.amazonaws.com/recognize', rekognitionData, function (text) {
      parent.find(".hover-buttons").toggle();
      const description = parent.find(".description");
      description.addClass("appear-animation").toggle();
      description.find('.description-text').text(text);
    });
  }

  function openPlayer() {
    const parent = $(this).closest(".image-card");
    const rekognitionData = new FormData();
    rekognitionData.append("imageKey", "<paste_image_key>");
    post('https://uw47difk8j.execute-api.eu-central-1.amazonaws.com/recognize', rekognitionData, function (text) {
      const pollyData = new FormData();
      pollyData.append("text", text);
      post('https://uw47difk8j.execute-api.eu-central-1.amazonaws.com/synthesize', pollyData, function (data) {
        parent.find(".hover-buttons").toggle();
        parent.find('.audio').addClass('appear-animation').append('<audio id="audio" src="' + data + '" controls></audio>').toggle();
      });
    });
  }

  function bindExpandedImage() {
    const parent = $(this).closest("img");
    $("#expanded-image").attr("src", parent.attr("src"));
  }

  function hideAppearingElements() {
    $(".description").hide();
    $(".audio").empty().hide();
    $(".hover-buttons").show();
  }

  // Show hover buttons for mobile devices
  function bindForMobileDevices() {
    if ($(window).width() < 992) {
      $(".hover-buttons").css("opacity", "1");
      $(".description, .audio").on("click", hideAppearingElements);
    }
  }

  function get(url, callback) {
    fetch(url, {
      method: "GET",
      headers: {
        Accept: "application/json, text/plain",
      },
    })
      .then((response) => response.json())
      .then((data) => callback(data))
      .catch((error) => {
        console.error("Error GET:", error);
      });
  }

  function post(url, formData, callback) {
    fetch("url", {
      method: "POST",
      body: formData,
    })
      .then((data) => callback(data))
      .catch((error) => {
        console.error("Error POST:", error);
      });
  }

});